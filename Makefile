PROFILE    := investigator-ai
NAMESPACE  := investigator-ai
KUBECTL    := kubectl -n $(NAMESPACE)
MVN        := mvn -f apps/pom.xml
PID_FILE   := .forward.pids

# NodePort addresses (reachable after `make port-forward`)
API_URL    := http://localhost:8080
WEB_URL    := http://localhost:8090

.DEFAULT_GOAL := help

.PHONY: help up down build deploy infra seed investigate \
        port-forward stop-forward status logs-api logs-web k9s \
        check-ollama _gen-secrets _wait-infra _wait-app

# ---------------------------------------------------------------
# Public targets
# ---------------------------------------------------------------

help:
	@echo ""
	@echo "InvestigatorAI"
	@echo ""
	@echo "  make up           full setup: ollama check + minikube + infra + build + deploy + port-forward"
	@echo "  make seed         populate Neo4j with the Ferretti investigation scenario"
	@echo "  make investigate  run the example query against the seeded data"
	@echo "  make down         delete minikube profile (destroys all data)"
	@echo ""
	@echo "  make build        rebuild container images and reload into minikube"
	@echo "  make deploy       re-apply k8s manifests (no image rebuild)"
	@echo "  make port-forward start background port-forwards"
	@echo "  make stop-forward kill background port-forwards"
	@echo "  make status       show pod and service status"
	@echo "  make logs-api     tail investigator-api logs"
	@echo "  make logs-web     tail investigator-web logs"
	@echo "  make k9s          open k9s on the investigator-ai cluster"
	@echo ""

# Full one-shot setup
up: check-ollama _gen-secrets
	@echo "==> Starting minikube (profile: $(PROFILE))"
	minikube start \
	  --profile $(PROFILE) \
	  --driver docker \
	  --cpus 4 \
	  --memory 6144 \
	  --disk-size 30g \
	  --kubernetes-version v1.31.0 || true
	minikube addons enable ingress        --profile $(PROFILE)
	minikube addons enable metrics-server --profile $(PROFILE)
	kubectl config use-context $(PROFILE)

	@echo "==> Applying namespace, secrets, and configmap"
	kubectl apply -f k8s/namespace.yaml
	$(KUBECTL) apply -f k8s/secret.yaml
	$(KUBECTL) apply -f k8s/configmap.yaml

	@echo "==> Deploying infrastructure"
	$(KUBECTL) apply -f k8s/neo4j/
	$(KUBECTL) apply -f k8s/qdrant/
	$(KUBECTL) apply -f k8s/postgres/
	$(KUBECTL) apply -f k8s/langfuse/
	@$(MAKE) _wait-infra

	@echo "==> Building container images inside minikube"
	@$(MAKE) build

	@echo "==> Deploying application"
	$(KUBECTL) apply -f k8s/api/
	$(KUBECTL) apply -f k8s/web/
	@$(MAKE) _wait-app

	@$(MAKE) port-forward

	@echo ""
	@echo "==> Done. Services:"
	@echo "    investigator-api  $(API_URL)/actuator/health/readiness"
	@echo "    investigator-web  $(WEB_URL)"
	@echo "    Neo4j browser     http://localhost:7474"
	@echo "    Langfuse          http://localhost:3000"
	@echo "    Qdrant            http://localhost:6333/dashboard"
	@echo ""
	@echo "    Run 'make seed' to load the Ferretti corruption scenario"
	@echo "    Run 'make investigate' to execute the example investigation"
	@echo ""

# Bootstrap Langfuse (admin user + project + API keys) — idempotent
seed-langfuse:
	@bash scripts/seed-langfuse.sh

# Seed Neo4j with the Ferretti corruption scenario
seed:
	@echo "==> Seeding graph..."
	@bash scripts/seed-data.sh
	@echo "==> Seeding vector documents..."
	@curl -fsS -X POST $(WEB_URL)/api/web/v1/admin/seed-documents \
	  -H "Content-Type: application/json" || \
	  echo "(skipped — investigator-web not reachable on $(WEB_URL); start it and re-run 'curl -X POST $(WEB_URL)/api/web/v1/admin/seed-documents')"
	@echo ""
	@echo "==> Bootstrapping Langfuse..."
	@bash scripts/seed-langfuse.sh

# Fire the pre-built investigation query
investigate:
	@echo "==> Running investigation against seeded data..."
	@echo ""
	curl -s -X POST $(API_URL)/api/v1/investigate \
	  -H "Content-Type: application/json" \
	  -d '{ \
	    "query": "Chi controlla realmente Costruzioni Ferretti Srl e ci sono conflitti di interesse con l'\''ex sindaco Luigi Conti nell'\''aggiudicazione degli appalti pubblici del Comune di Brescia nel periodo 2022-2023?", \
	    "depth": 4, \
	    "focus_entities": ["Costruzioni Ferretti Srl", "Marco Ferretti", "Luigi Conti"] \
	  }' | python3 -m json.tool 2>/dev/null || \
	curl -s -X POST $(API_URL)/api/v1/investigate \
	  -H "Content-Type: application/json" \
	  -d '{ \
	    "query": "Chi controlla realmente Costruzioni Ferretti Srl e ci sono conflitti di interesse con l'\''ex sindaco Luigi Conti nell'\''aggiudicazione degli appalti pubblici del Comune di Brescia nel periodo 2022-2023?", \
	    "depth": 4, \
	    "focus_entities": ["Costruzioni Ferretti Srl", "Marco Ferretti", "Luigi Conti"] \
	  }'
	@echo ""

# Tear down everything
down: stop-forward
	minikube delete --profile $(PROFILE)
	@rm -f $(PID_FILE)

# ---------------------------------------------------------------
# Build & deploy helpers
# ---------------------------------------------------------------

build:
	@echo "==> Packaging fat jars"
	$(MVN) -pl investigator-api,investigator-web -am package -Dmaven.test.skip=true
	@echo "==> Building container images inside minikube docker daemon"
	eval $$(minikube docker-env --profile $(PROFILE)) && \
	docker build -t investigator-ai:latest apps/investigator-api/
	eval $$(minikube docker-env --profile $(PROFILE)) && \
	docker build -t investigator-web:latest apps/investigator-web/

deploy:
	$(KUBECTL) apply -f k8s/api/
	$(KUBECTL) apply -f k8s/web/
	$(KUBECTL) rollout restart deployment/investigator-api
	$(KUBECTL) rollout restart deployment/investigator-web

# ---------------------------------------------------------------
# Port-forward management
# ---------------------------------------------------------------

port-forward: stop-forward
	@echo "==> Starting port-forwards (background)"
	@mkdir -p "$(dir $(PID_FILE))"
	@kubectl port-forward svc/api       8080:8080 -n $(NAMESPACE) > /dev/null 2>&1 & echo $$! >> $(PID_FILE)
	@kubectl port-forward svc/investigator-web 8090:8090 -n $(NAMESPACE) > /dev/null 2>&1 & echo $$! >> $(PID_FILE)
	@kubectl port-forward svc/neo4j     7474:7474 -n $(NAMESPACE) > /dev/null 2>&1 & echo $$! >> $(PID_FILE)
	@kubectl port-forward svc/neo4j     7687:7687 -n $(NAMESPACE) > /dev/null 2>&1 & echo $$! >> $(PID_FILE)
	@kubectl port-forward svc/langfuse  3000:3000 -n $(NAMESPACE) > /dev/null 2>&1 & echo $$! >> $(PID_FILE)
	@kubectl port-forward svc/qdrant    6333:6333 -n $(NAMESPACE) > /dev/null 2>&1 & echo $$! >> $(PID_FILE)
	@sleep 1
	@echo "    Port-forwards active. PIDs stored in $(PID_FILE)"

stop-forward:
	@if [ -f $(PID_FILE) ]; then \
	  echo "==> Stopping port-forwards"; \
	  xargs kill 2>/dev/null < $(PID_FILE) || true; \
	  rm -f $(PID_FILE); \
	fi

# ---------------------------------------------------------------
# Observability
# ---------------------------------------------------------------

status:
	@echo "--- Pods ---"
	$(KUBECTL) get pods
	@echo ""
	@echo "--- Services ---"
	$(KUBECTL) get svc

logs-api:
	$(KUBECTL) logs -f deployment/investigator-api

logs-web:
	$(KUBECTL) logs -f deployment/investigator-web

k9s:
	k9s --context $(PROFILE) --namespace $(NAMESPACE)

# ---------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------

check-ollama:
	@command -v ollama > /dev/null 2>&1 || { \
	  echo ""; \
	  echo "ERROR: ollama not installed."; \
	  echo "  brew install ollama"; \
	  echo "  — or — https://ollama.com/download"; \
	  echo ""; \
	  exit 1; \
	}
	@curl -sf http://localhost:11434/api/tags > /dev/null 2>&1 || { \
	  echo "==> Starting ollama server..."; \
	  ollama serve > /dev/null 2>&1 & sleep 3; \
	}
	@ollama list 2>/dev/null | grep -q "qwen3.6:35b" || { \
	  echo "==> Pulling qwen3.6:35b (~24 GB, one-time download)..."; \
	  ollama pull qwen3.6:35b; \
	}
	@ollama list 2>/dev/null | grep -q "nomic-embed-text" || { \
	  echo "==> Pulling nomic-embed-text (embedding model, ~270 MB)..."; \
	  ollama pull nomic-embed-text; \
	}
	@echo "    Ollama ready: qwen3.6:35b + nomic-embed-text"

_gen-secrets:
	@bash scripts/gen-secrets.sh

_wait-infra:
	@echo "==> Waiting for infrastructure pods"
	$(KUBECTL) rollout status deployment/neo4j               --timeout=120s
	$(KUBECTL) rollout status deployment/qdrant              --timeout=60s
	$(KUBECTL) rollout status deployment/postgres            --timeout=60s
	$(KUBECTL) rollout status deployment/langfuse-postgres   --timeout=60s
	$(KUBECTL) rollout status deployment/langfuse-clickhouse --timeout=120s
	$(KUBECTL) rollout status deployment/langfuse-redis      --timeout=60s
	$(KUBECTL) rollout status deployment/langfuse-minio      --timeout=60s
	$(KUBECTL) rollout status deployment/langfuse            --timeout=180s
	$(KUBECTL) rollout status deployment/langfuse-worker     --timeout=120s

_wait-app:
	@echo "==> Waiting for application pods"
	$(KUBECTL) rollout status deployment/investigator-api --timeout=180s
	$(KUBECTL) rollout status deployment/investigator-web --timeout=180s
