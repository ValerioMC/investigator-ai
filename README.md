# InvestigatorAI

Journalistic investigation assistant combining a relationship graph (Neo4j),
vector search (Qdrant), and a multi-agent LLM pipeline (Spring Boot + LangChain4j)
to answer complex investigative queries like:

> "Who really controls Ferretti Construction Ltd and are there any conflicts
> of interest with the former mayor in the award of public contracts?"

---

## Quick start

### Prerequisites

- Docker Desktop running
- `minikube`, `kubectl`, `ollama`, Java 21, Maven 3.9+

`make up` will pull `qwen3.6:35b` (~24 GB) and `nomic-embed-text` (~270 MB) via
Ollama on first run if they are not already present.

### 1. Spin up everything

```bash
make up
```

This single command:
- Creates a minikube profile named `investigator-ai` (4 CPU / 6 GB RAM)
- Enables ingress and metrics-server addons
- Auto-generates `k8s/secret.yaml` with random credentials (skipped if the file already exists)
- Deploys Neo4j, Qdrant, PostgreSQL, and Langfuse
- Builds both Spring Boot container images inside the minikube daemon (no registry needed)
- Deploys `investigator-api` (port 8080) and `investigator-web` (port 8090)
- Starts background port-forwards for all services

Takes ~10 minutes on first run (image pulls + Maven build).

### 2. Load the investigation scenario

```bash
make seed
```

Populates Neo4j with a fictional corruption scenario (City of Brescia, 2022-2023)
and bootstraps Langfuse with the default admin user, project, and API keys:

| Entity | Type | Details |
|--------|------|---------|
| Marco Ferretti | Person | City Councillor, risk 0.82 |
| Luigi Conti | Person | Former Mayor (2016-2024), risk 0.75 |
| Mario Conti | Person | Brother of Luigi, 15% stake in LuxHold |
| Ferretti Construction Ltd | Company | Italian construction firm |
| LuxHold SA | Company | Luxembourg holding, tax haven |
| Esposito Offshore Ltd | Company | Luxembourg shell, inactive since 2021 |
| Loggia Square Redevelopment | Contract | €1.2M, awarded April 2022 |
| Urban Road Network Maintenance | Contract | €450K, awarded January 2023 |

Graph loaded: 11 nodes, 14 relationships including ownership chains,
family ties, and contract award paths.

### 3. Run the investigation

```bash
make investigate
```

This sends the following query to the SupervisorAgent:

> **"Who really controls Ferretti Construction Ltd and are there any conflicts
> of interest with former mayor Luigi Conti in the award of public contracts
> by the City of Brescia in the 2022-2023 period?"**
>
> Focus entities: `Ferretti Construction Ltd`, `Marco Ferretti`, `Luigi Conti`
> Depth: 4

The `InvestigationOrchestrator` runs two phases per domain:

**Phase 1 — deterministic Java data collection** (no LLM, no hallucination risk):
- entity resolution against the graph (persons vs. companies, detected from query text)
- year/date-range extraction via regex
- Neo4j traversal and Qdrant search via domain-specific `*AgentTool` classes

**Phase 2 — LLM synthesis** (each subagent receives pre-collected data and emits a JSON `AgentReport`):
1. **CorporateAgent** — ownership chain `Ferretti → LuxHold SA → Ferretti Construction Ltd`
2. **PersonProfileAgent** — Conti's mayoral role + family link to LuxHold
3. **FinancialFlowAgent** — EBITDA anomaly (+340% vs. sector avg) in contract years
4. **DocumentAgent** — semantic match across indexed docs
5. **SourceVerificationAgent** — cross-link and confidence scoring

All five JSON reports are bundled and fed to **SupervisorAgent** for final merge into `InvestigationReport`.

Expected findings (from seeded graph):
- `HIGH` — Ferretti is 77% beneficial owner of Ferretti Construction Ltd via LuxHold SA
- `HIGH` — Luigi Conti voted on both contracts without declaring a conflict of interest (brother Mario owns 15% of LuxHold)
- `MEDIUM` — Esposito Offshore (inactive) retains 8% stake in LuxHold — possible ownership-concealment vehicle

You can also open the Vue SPA at **http://localhost:8090** and submit
the same query interactively from the Investigation view.

### 4. Verify traces in Langfuse

After `make investigate` completes, open Langfuse to inspect the full orchestration tree:

1. Open **http://localhost:3000**
2. Log in: `admin@investigator-ai.local` / `investigator`
3. Navigate to the **investigator-ai** project → **Traces**

Each investigation produces a single umbrella trace with this span structure:

```
Investigation (trace)
  └─ resolve-entities-and-window    ← deterministic Java, no LLM
  └─ step-corporate                 ← span wrapping CorporateAgent synthesis
       └─ CorporateAgent (generation)
  └─ step-person
       └─ PersonProfileAgent (generation)
  └─ step-financial
       └─ FinancialFlowAgent (generation)
  └─ step-document
       └─ DocumentAgent (generation)
  └─ step-verification
       └─ SourceVerificationAgent (generation)
  └─ step-supervisor
       └─ SupervisorAgent (generation)
```

Each span carries the full pre-collected payload as input and the JSON report as output.
Langfuse observability is optional — disable with `langfuse.enabled=false`.

---

## All make targets

```
make up              full setup: ollama check + minikube + infra + build + deploy + port-forward
make seed            load Ferretti scenario into Neo4j + bootstrap Langfuse
make seed-langfuse   bootstrap Langfuse only (admin user, project, API keys) — idempotent
make investigate     run the example query (curl to localhost:8080)
make down            delete minikube profile (destroys all data)

make build           rebuild container images (Maven package + docker build inside minikube)
make deploy          re-apply k8s manifests and restart pods (no rebuild)
make port-forward    restart background port-forwards
make stop-forward    kill all background port-forwards
make status          show pods and services
make logs-api        tail investigator-api
make logs-web        tail investigator-web
make k9s             open k9s on the investigator-ai cluster
```

### Port-forward map (after `make up` or `make port-forward`)

| Service | URL |
|---------|-----|
| investigator-api REST | http://localhost:8080 |
| investigator-web SPA | http://localhost:8090 |
| Neo4j browser | http://localhost:7474 |
| Neo4j Bolt | bolt://localhost:7687 |
| Langfuse traces | http://localhost:3000 |
| Qdrant dashboard | http://localhost:6333/dashboard |

---

## Manual API call

```bash
curl -s -X POST http://localhost:8080/api/v1/investigate \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Who really controls Ferretti Construction Ltd and are there any conflicts of interest with former mayor Luigi Conti in the award of public contracts by the City of Brescia in the 2022-2023 period?",
    "depth": 4,
    "focusEntities": ["Ferretti Construction Ltd", "Marco Ferretti", "Luigi Conti"]
  }' | python3 -m json.tool
```

### Sample response

Captured from an actual run on the seeded scenario (qwen3.6:35b, ~6 minutes end-to-end across the five subagents + supervisor merge; 53 findings emitted, abridged here to one per agent source):

```json
{
  "summary": "Marco Ferretti is the primary controller of Ferretti Construction Ltd, holding 77.0% of shares indirectly via LuxHold SA, while Mario Conti holds 15.0%. Luigi Conti, Mario Conti's brother, held a role at the City of Brescia during the period when Ferretti Construction Ltd was awarded two public contracts totaling €1,650,000. Multiple agents identified a conflict of interest regarding Luigi Conti's role at the City of Brescia coinciding with these contract awards, although no direct ownership link between Luigi Conti and the company was established.",
  "findings": [
    {
      "claim": "Marco Ferretti indirectly owns 77.0% of Ferretti Construction Ltd via LuxHold SA.",
      "confidence": "HIGH",
      "evidence": ["- Marco Ferretti → OWNS 77.0% (indirect via LuxHold SA, IT)"],
      "agent_source": "CorporateAgent"
    },
    {
      "claim": "Marco Ferretti controls Ferretti Construction Ltd.",
      "confidence": "HIGH",
      "evidence": ["COMPANIES CONTROLLED BY Marco Ferretti:", "- Ferretti Construction Ltd (Ltd, IT)"],
      "agent_source": "PersonProfileAgent"
    },
    {
      "claim": "Ferretti Construction Ltd won the 'Loggia Square Redevelopment — Phase II' contract for €1,200,000 from the City of Brescia on 2022-04-15.",
      "confidence": "HIGH",
      "evidence": ["- Loggia Square Redevelopment — Phase II: €1,200,000 from City of Brescia (2022-04-15)"],
      "agent_source": "FinancialFlowAgent"
    },
    {
      "claim": "The court record 'brescia-prosecutor-ferretti-2023.txt' references entities p-002, p-003, c-001, c-002, and pb-001 with a relevance score of 0.520.",
      "confidence": "MEDIUM",
      "evidence": ["[court_record] brescia-prosecutor-ferretti-2023.txt (score: 0.520)"],
      "agent_source": "DocumentAgent"
    },
    {
      "claim": "Marco Ferretti is directly connected to Ferretti Construction Ltd in the graph (1 hop).",
      "confidence": "HIGH",
      "evidence": ["CONNECTION PATH (1 hops): Marco Ferretti → Ferretti Construction Ltd"],
      "agent_source": "SourceVerificationAgent"
    }
  ],
  "entity_map": {
    "persons": ["Marco Ferretti", "Mario Conti", "Luigi Conti"],
    "companies": ["Ferretti Construction Ltd", "LuxHold SA"],
    "contracts": ["Loggia Square Redevelopment — Phase II", "Urban Road Network Extraordinary Maintenance"]
  },
  "recommended_follow_ups": [
    "Verify Luigi Conti's specific title and decision-making authority at the City of Brescia during 2022-2023 to confirm the nature of the conflict of interest.",
    "Investigate the identity of the unknown entities holding the remaining 8.0% of LuxHold SA to determine if they are linked to other public officials.",
    "Review the 'conti-statement-2022.txt' official filing for explicit declarations of interest regarding Ferretti Construction Ltd contracts."
  ],
  "disclaimer": "This report is a journalistic aid. All claims require independent editorial verification before publication."
}
```

---

## Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Spring Boot 4.0 + Java 21 virtual threads |
| AI | LangChain4j 1.14.1 + Ollama (qwen3.6:35b, think=false) |
| Graph | Neo4j 5.x — Spring Data Neo4j 8, all Cypher in `@Query` on a single repo interface |
| Vectors | Qdrant 1.x — 512-token chunks, nomic-embed-text embeddings |
| Persistence | PostgreSQL 16 + Spring Data JDBC + Liquibase |
| Frontend | Vue 3 + Vite + Tailwind CSS + Cytoscape.js |
| Observability | Langfuse v3 self-hosted (custom `LangfuseClient`) + Micrometer + Actuator |
| K8s | Minikube, profile `investigator-ai`, `imagePullPolicy: Never` |

## Modules

| Module | Description |
|--------|-------------|
| `investigator-domain` | Pure Java 21 domain model — records, sealed errors, report types |
| `investigator-graph` | Neo4j repository — all Cypher in `@Query` annotations |
| `investigator-vector` | Qdrant chunker, embedder, vector repository |
| `investigator-agents` | Orchestrator + 5 LangChain4j agents + domain tool collectors + Langfuse client |
| `investigator-api` | `POST /api/v1/investigate` entry point |
| `investigator-web` | Vue 3 SPA + REST backend (sessions, entities, graph, history) |

### investigator-agents internals

```
InvestigationOrchestrator       ← main entry point, Java orchestration (no LLM in the hot path)
  ├── *AgentTool classes         ← data collectors: hit Neo4j + Qdrant, return formatted strings
  └── *Agent interfaces          ← LLM synthesis only, receive pre-collected text, emit JSON AgentReport

AgentConfiguration              ← two ChatModel beans:
  ├── ollamaChatModel (primary)  ← default, all sub-agents
  └── supervisorChatModel        ← responseFormat=JSON, numPredict=16384 (prevents mid-array truncation)

LangfuseClient                  ← thin HTTP client for Langfuse ingestion API (fire-and-forget, virtual threads)
LangfuseObservabilityListener   ← LangChain4j listener, attaches generation spans to parent span from context
```
