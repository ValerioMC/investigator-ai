#!/usr/bin/env bash
# Waits for Langfuse v3 (web + worker) to be ready and smoke-tests the health endpoint.
# Org/project/admin are auto-provisioned via LANGFUSE_INIT_* env vars on first start —
# no manual SQL seeding needed in v3.
set -euo pipefail

NAMESPACE=investigator-ai
PK="pk-lf-aff8d7ab38916d82803ee7568704002d"

echo "==> Waiting for Langfuse web to be ready..."
kubectl -n "$NAMESPACE" rollout status deployment/langfuse --timeout=180s

echo "==> Waiting for Langfuse worker to be ready..."
kubectl -n "$NAMESPACE" rollout status deployment/langfuse-worker --timeout=120s

# Smoke-test via the ClusterIP service (avoids localhost-in-exec quirk in some container runtimes)
LF_SVC_IP=$(kubectl -n "$NAMESPACE" get svc langfuse -o jsonpath='{.spec.clusterIP}')
LANGFUSE_POD=$(kubectl -n "$NAMESPACE" get pod -l app=langfuse --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
echo "==> Health check via service $LF_SVC_IP (pod $LANGFUSE_POD)..."
kubectl -n "$NAMESPACE" exec "$LANGFUSE_POD" -- \
  wget -qO- "http://${LF_SVC_IP}:3000/api/public/health" 2>/dev/null | grep -qi '"status":"ok"' && \
  echo "    Health: OK" || echo "    Health check: unexpected response"

echo ""
echo "==> Langfuse v3 ready."
echo "    UI:       http://localhost:3000  (after: make port-forward)"
echo "    Email:    admin@investigator-ai.local"
echo "    Password: investigator"
echo "    API key:  $PK"
