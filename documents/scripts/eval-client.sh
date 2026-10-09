#!/usr/bin/env bash
# Клиент documents-eval (публичный, выдача токена по паролю, aud documents-api) — ТОЛЬКО для стенда замера и CI:
# eval/run.py читает поля как оператор и сам обновляет токен (EVAL_TOKEN_URL). На стенде защиты не запускать.
# Запуск из любого места: scripts/eval-client.sh  → печатает строку EVAL_TOKEN_URL=...
set -euo pipefail
cd "$(dirname "$0")/../infra"
set -a; . ./.env; set +a
kc() { docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080/auth --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null
if ! kc get clients -r documents -q clientId=documents-eval --fields id | grep -q '"id"'; then
  kc create clients -r documents -f - >/dev/null <<'JSON'
{"clientId": "documents-eval", "name": "Замер качества (только стенд замера)", "publicClient": true,
 "standardFlowEnabled": false, "directAccessGrantsEnabled": true,
 "protocolMappers": [{"name": "aud-documents-api", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
   "config": {"included.custom.audience": "documents-api", "access.token.claim": "true", "id.token.claim": "false"}}]}
JSON
fi
echo "EVAL_TOKEN_URL=https://${PUBLIC_HOST}/auth/realms/documents/protocol/openid-connect/token"
