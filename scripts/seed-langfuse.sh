#!/usr/bin/env bash
# Bootstraps Langfuse with a default admin user, org, project, and the API keys
# already present in investigator-secrets. Idempotent — safe to run multiple times.
set -euo pipefail

NAMESPACE=investigator-ai
PK="pk-lf-aff8d7ab38916d82803ee7568704002d"
SK="sk-lf-bbc2dd84513185979b6cd3cc871eaec6217bafe374247eaba834325bfeed48a2"
ADMIN_EMAIL="admin@investigator-ai.local"
ADMIN_PASSWORD="investigator"

echo "==> Waiting for Langfuse to be ready..."
kubectl -n "$NAMESPACE" rollout status deployment/langfuse --timeout=120s

LANGFUSE_POD=$(kubectl -n "$NAMESPACE" get pod -l app=langfuse -o jsonpath='{.items[0].metadata.name}')

echo "==> Generating credential hashes..."
HASHES=$(kubectl -n "$NAMESPACE" exec "$LANGFUSE_POD" -- node -e "
const bcrypt = require('/app/node_modules/.pnpm/bcryptjs@2.4.3/node_modules/bcryptjs');
const crypto = require('crypto');
const sk = '${SK}';
const pwd = '${ADMIN_PASSWORD}';
Promise.all([bcrypt.hash(pwd, 12), bcrypt.hash(sk, 11)]).then(([pwdHash, skHash]) => {
  const skSha = crypto.createHash('sha256').update(sk).digest('hex');
  process.stdout.write(pwdHash + '|' + skHash + '|' + skSha);
});
")

PWD_HASH=$(echo "$HASHES" | cut -d'|' -f1)
SK_BCRYPT=$(echo "$HASHES" | cut -d'|' -f2)
SK_SHA256=$(echo "$HASHES" | cut -d'|' -f3)
SK_DISPLAY="...${SK: -4}"

echo "==> Seeding Langfuse database..."

# Write SQL to a temp file, copy into the postgres pod, execute
SQL_FILE=$(mktemp /tmp/langfuse-seed-XXXXXX.sql)
cat > "$SQL_FILE" <<SQLEOF
-- Ensure admin user exists (may have been created via signup UI)
INSERT INTO users (id, name, email, email_verified, password, admin)
  VALUES ('user-admin-investigator', 'Admin', '${ADMIN_EMAIL}', NOW(), '${PWD_HASH}', true)
  ON CONFLICT (email) DO UPDATE SET admin = true, password = EXCLUDED.password;

-- Use actual user ID for all subsequent inserts
DO \$\$
DECLARE
  v_user_id  text := (SELECT id FROM users WHERE email = '${ADMIN_EMAIL}');
  v_mem_id   text := 'mem-admin-investigator';
BEGIN
  INSERT INTO organizations (id, name)
    VALUES ('org-investigator-ai', 'InvestigatorAI')
    ON CONFLICT (id) DO NOTHING;

  INSERT INTO organization_memberships (id, user_id, org_id, role)
    VALUES (v_mem_id, v_user_id, 'org-investigator-ai', 'OWNER')
    ON CONFLICT (org_id, user_id) DO UPDATE SET id = v_mem_id;

  -- Re-fetch the actual membership id (may have been pre-existing with different id)
  v_mem_id := (SELECT id FROM organization_memberships WHERE org_id = 'org-investigator-ai' AND user_id = v_user_id);

  INSERT INTO projects (id, name, org_id)
    VALUES ('proj-investigator-ai', 'investigator-ai', 'org-investigator-ai')
    ON CONFLICT (id) DO NOTHING;

  INSERT INTO project_memberships (user_id, project_id, org_membership_id, role)
    VALUES (v_user_id, 'proj-investigator-ai', v_mem_id, 'OWNER')
    ON CONFLICT (project_id, user_id) DO NOTHING;

  INSERT INTO api_keys (id, public_key, hashed_secret_key, display_secret_key, fast_hashed_secret_key, project_id, note)
    VALUES ('key-investigator-ai', '${PK}', '${SK_BCRYPT}', '${SK_DISPLAY}', '${SK_SHA256}', 'proj-investigator-ai', 'default')
    ON CONFLICT (id) DO NOTHING;
END \$\$;
SQLEOF

POSTGRES_POD=$(kubectl -n "$NAMESPACE" get pod -l app=langfuse-postgres -o jsonpath='{.items[0].metadata.name}')
kubectl cp "$SQL_FILE" "${NAMESPACE}/${POSTGRES_POD}:/tmp/langfuse-seed.sql"
kubectl -n "$NAMESPACE" exec "$POSTGRES_POD" -- psql -U langfuse -d langfuse -q -f /tmp/langfuse-seed.sql
kubectl -n "$NAMESPACE" exec "$POSTGRES_POD" -- rm /tmp/langfuse-seed.sql
rm "$SQL_FILE"

echo "==> Langfuse ready."
echo "    UI:       http://localhost:3000  (after: make port-forward)"
echo "    Email:    ${ADMIN_EMAIL}"
echo "    Password: ${ADMIN_PASSWORD}"
