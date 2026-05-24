#!/usr/bin/env bash
set -euo pipefail

PROFILE="investigator-ai"
NAMESPACE="investigator-ai"

echo "=== Creating Minikube profile: $PROFILE ==="
minikube start \
  --profile "$PROFILE" \
  --driver docker \
  --cpus 4 \
  --memory 6144 \
  --disk-size 30g \
  --kubernetes-version v1.31.0

echo "=== Enabling addons ==="
minikube addons enable ingress --profile "$PROFILE"
minikube addons enable metrics-server --profile "$PROFILE"

echo "=== Switching kubectl context ==="
kubectl config use-context "$PROFILE"

echo "=== Creating namespace ==="
kubectl apply -f k8s/namespace.yaml

echo "=== Checking for secret.yaml ==="
if [ ! -f k8s/secret.yaml ]; then
  echo "ERROR: k8s/secret.yaml not found."
  echo "Copy k8s/secret.yaml.template to k8s/secret.yaml and fill in real values."
  exit 1
fi

echo "=== Applying secrets and configmap ==="
kubectl apply -f k8s/secret.yaml -n "$NAMESPACE"
kubectl apply -f k8s/configmap.yaml -n "$NAMESPACE"

echo "=== Deploying infrastructure ==="
kubectl apply -f k8s/neo4j/ -n "$NAMESPACE"
kubectl apply -f k8s/qdrant/ -n "$NAMESPACE"
kubectl apply -f k8s/langfuse/ -n "$NAMESPACE"
kubectl apply -f k8s/postgres/ -n "$NAMESPACE"

echo "=== Waiting for infrastructure to be ready ==="
kubectl rollout status deployment/neo4j -n "$NAMESPACE" --timeout=120s
kubectl rollout status deployment/qdrant -n "$NAMESPACE" --timeout=60s
kubectl rollout status deployment/postgres -n "$NAMESPACE" --timeout=60s

echo "=== Building and loading app images into Minikube ==="
eval "$(minikube docker-env --profile $PROFILE)"

mvn -f apps/pom.xml -pl investigator-api -am package -DskipTests \
    -Dquarkus.container-image.build=true \
    -Dquarkus.container-image.name=investigator-ai \
    -Dquarkus.container-image.tag=latest \
    -Dquarkus.container-image.group=""

mvn -f apps/pom.xml -pl investigator-web -am package -DskipTests \
    -Dquarkus.container-image.build=true \
    -Dquarkus.container-image.name=investigator-web \
    -Dquarkus.container-image.tag=latest \
    -Dquarkus.container-image.group=""

echo "=== Deploying services ==="
kubectl apply -f k8s/api/ -n "$NAMESPACE"
kubectl apply -f k8s/web/ -n "$NAMESPACE"

echo ""
echo "=== Done! ==="
echo "investigator-api: http://$(minikube ip --profile $PROFILE):30080"
echo "investigator-web: http://$(minikube ip --profile $PROFILE):30090  (Vue SPA + REST)"
echo "Neo4j:    kubectl port-forward svc/neo4j 7474:7474 7687:7687 -n $NAMESPACE"
echo "Langfuse: kubectl port-forward svc/langfuse 3000:3000 -n $NAMESPACE"
echo ""
echo "Add to /etc/hosts:"
echo "  $(minikube ip --profile $PROFILE)  investigator-ai.local"
