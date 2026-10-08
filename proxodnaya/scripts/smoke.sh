#!/usr/bin/env bash
# Быстрый смоук стенда. Использование: scripts/smoke.sh https://host
set -euo pipefail
H="${1:-https://localhost}"; K="${KEYCLOAK_URL:-${H/\/\//\/\/auth.}}"
ok() { echo "✔ $1"; } ; bad() { echo "✖ $1"; exit 1; }
curl -fsSk "$H/api/actuator/health" | grep -q '"UP"' && ok "backend health" || bad "backend health"
curl -fsSk "$K/realms/gate/.well-known/openid-configuration" >/dev/null && ok "keycloak realm gate" || bad "keycloak"
for app in admin kiosk worker; do curl -fsSk "$H/$app/" | grep -qi '<div id="root"' && ok "frontend /$app" || bad "frontend /$app"; done
code=$(curl -sk -o /dev/null -w '%{http_code}' "$H/api/attempts"); [ "$code" = 401 ] && ok "API закрыт без токена" || bad "API без токена вернул $code"
echo "ℹ дальше — маршрут судьи из docs/DEMO.md руками"
