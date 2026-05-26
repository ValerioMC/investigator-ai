# InvestigatorAI

Journalistic investigation assistant combining a relationship graph (Neo4j),
vector search (Qdrant), and a multi-agent LLM pipeline (Spring Boot + LangChain4j)
to answer complex investigative queries like:

> "Chi controlla realmente Costruzioni Ferretti Srl e ci sono conflitti di
> interesse con l'ex sindaco nell'aggiudicazione degli appalti pubblici?"

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

Populates Neo4j with a realistic corruption scenario (Brescia, 2022-2023) and
bootstraps Langfuse with the default admin user, project, and API keys:

| Entity | Type | Details |
|--------|------|---------|
| Marco Ferretti | Person | Consigliere comunale, risk 0.82 |
| Luigi Conti | Person | Ex-sindaco (2016-2024), risk 0.75 |
| Mario Conti | Person | Brother of Luigi, 15% stake in LuxHold |
| Costruzioni Ferretti Srl | Company | Italian construction firm |
| LuxHold SA | Company | Luxembourg holding, tax haven |
| Esposito Offshore Ltd | Company | Luxembourg shell, inactive since 2021 |
| Riqualificazione Piazza Loggia | Contract | €1.2M, awarded April 2022 |
| Manutenzione rete stradale | Contract | €450K, awarded January 2023 |

Graph loaded: 11 nodes, 14 relationships including ownership chains,
family ties, and contract award paths.

### 3. Run the investigation

```bash
make investigate
```

This sends the following query to the SupervisorAgent:

> **"Chi controlla realmente Costruzioni Ferretti Srl e ci sono conflitti
> di interesse con l'ex sindaco Luigi Conti nell'aggiudicazione degli
> appalti pubblici del Comune di Brescia nel periodo 2022-2023?"**
>
> Focus entities: `Costruzioni Ferretti Srl`, `Marco Ferretti`, `Luigi Conti`
> Depth: 4

The `InvestigationOrchestrator` runs two phases per domain:

**Phase 1 — deterministic Java data collection** (no LLM, no hallucination risk):
- entity resolution against the graph (persons vs. companies, detected from query text)
- year/date-range extraction via regex
- Neo4j traversal and Qdrant search via domain-specific `*AgentTool` classes

**Phase 2 — LLM synthesis** (each subagent receives pre-collected data and emits a JSON `AgentReport`):
1. **CorporateAgent** — ownership chain `Ferretti → LuxHold SA → Costruzioni Ferretti Srl`
2. **PersonProfileAgent** — Conti's mayoral role + family link to LuxHold
3. **FinancialFlowAgent** — EBITDA anomaly (+340% vs. sector avg) in contract years
4. **DocumentAgent** — semantic match across indexed docs
5. **SourceVerificationAgent** — cross-link and confidence scoring

All five JSON reports are bundled and fed to **SupervisorAgent** for final merge into `InvestigationReport`.

Expected findings (from seeded graph):
- `HIGH` — Ferretti is 77% beneficial owner of Costruzioni Ferretti Srl via LuxHold SA
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
    "query": "Chi controlla realmente Costruzioni Ferretti Srl e ci sono conflitti di interesse con l'\''ex sindaco Luigi Conti nell'\''aggiudicazione degli appalti pubblici del Comune di Brescia nel periodo 2022-2023?",
    "depth": 4,
    "focusEntities": ["Costruzioni Ferretti Srl", "Marco Ferretti", "Luigi Conti"]
  }' | python3 -m json.tool
```

### Response shape

```json
{
  "query": "...",
  "summary": "2-3 sentence executive summary",
  "findings": [
    {
      "claim": "Marco Ferretti è il beneficiario effettivo al 100% di Costruzioni Ferretti Srl tramite LuxHold SA",
      "confidence": "HIGH",
      "evidence": [
        "GraphPath: Ferretti->OWNS(77%)->LuxHold SA",
        "GraphPath: LuxHold SA->CONTROLS(100%)->Costruzioni Ferretti Srl"
      ],
      "agentSource": "CorporateAgent"
    }
  ],
  "entityMap": {
    "persons": ["Marco Ferretti", "Luigi Conti", "Mario Conti"],
    "companies": ["Costruzioni Ferretti Srl", "LuxHold SA"],
    "contracts": ["Riqualificazione Piazza Loggia — Fase II"]
  },
  "recommendedFollowUps": [
    "Verificare le dichiarazioni patrimoniali di Luigi Conti 2018-2024"
  ],
  "disclaimer": "Questo rapporto è uno strumento di supporto al giornalismo investigativo. Tutte le affermazioni richiedono verifica editoriale indipendente prima della pubblicazione."
}
```

---

## Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Spring Boot 4.0 + Java 21 virtual threads |
| AI | LangChain4j 1.14.1 + Ollama (qwen3.6:35b, think=false) |
| Graph | Neo4j 5.x — raw Java Driver, Cypher in `@Query` annotations |
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
