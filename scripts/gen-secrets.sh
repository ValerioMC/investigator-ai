#!/usr/bin/env bash
# Auto-generate k8s/secret.yaml with random credentials.
# Safe to re-run: exits immediately if the file already exists.
set -euo pipefail

if [ -f k8s/secret.yaml ]; then
  echo "==> k8s/secret.yaml already exists, skipping generation"
  exit 0
fi

echo "==> Auto-generating k8s/secret.yaml with random credentials..."

NEO4J_PASS=$(openssl rand -hex 20)
PG_PASS=$(openssl rand -hex 20)
LF_PUB="pk-lf-$(openssl rand -hex 16)"
LF_SEC="sk-lf-$(openssl rand -hex 32)"
LF_AUTH=$(openssl rand -base64 32 | tr -d '\n=+/')
LF_SALT=$(openssl rand -hex 16)
LF_ENC=$(openssl rand -hex 32)

python3 - <<PYEOF
import base64

def enc(s):
    return base64.b64encode(s.encode()).decode()

vals = {
    'NEO4J_PASSWORD':           enc('$NEO4J_PASS'),
    'LANGFUSE_PUBLIC_KEY':      enc('$LF_PUB'),
    'LANGFUSE_SECRET_KEY':      enc('$LF_SEC'),
    'LANGFUSE_NEXTAUTH_SECRET': enc('$LF_AUTH'),
    'LANGFUSE_SALT':            enc('$LF_SALT'),
    'LANGFUSE_ENCRYPTION_KEY':  enc('$LF_ENC'),
    'POSTGRES_PASSWORD':        enc('$PG_PASS'),
}

with open('k8s/secret.yaml.template') as f:
    content = f.read()

for k, v in vals.items():
    content = content.replace(k + ': Q0hBTkdFX01F', k + ': ' + v)

# drop ANTHROPIC_API_KEY — not needed with local Ollama
lines = [l for l in content.splitlines() if 'ANTHROPIC_API_KEY' not in l]
with open('k8s/secret.yaml', 'w') as f:
    f.write('\n'.join(lines) + '\n')

print('    k8s/secret.yaml created')
PYEOF
