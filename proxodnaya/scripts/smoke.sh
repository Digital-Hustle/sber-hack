#!/usr/bin/env bash
# Быстрый смоук стенда. Использование: scripts/smoke.sh https://host
set -euo pipefail
H="${1:-https://localhost}"; K="${KEYCLOAK_URL:-${H/\/\//\/\/auth.}}"
ok() { echo "✔ $1"; } ; bad() { echo "✖ $1"; exit 1; }
curl -fsSk "$H/api/gateway/health" | grep -q '"UP"' && ok "gateway health" || bad "gateway health"
curl -fsSk "$K/realms/gate/.well-known/openid-configuration" >/dev/null && ok "keycloak realm gate" || bad "keycloak"
for app in admin kiosk worker; do curl -fsSk "$H/$app/" | grep -qi '<div id="root"' && ok "frontend /$app" || bad "frontend /$app"; done
for svc in people access shift assistant notify; do
  code=$(curl -sk -o /dev/null -w '%{http_code}' "$H/api/$svc/actuator/health"); [ "$code" = 200 ] && ok "$svc health" || bad "$svc health: $code"
done
code=$(curl -sk -o /dev/null -w '%{http_code}' "$H/api/access/attempts"); [ "$code" = 401 ] && ok "API закрыт без токена" || bad "API без токена вернул $code"
code=$(curl -sk -o /dev/null -w '%{http_code}' "$H/api/access/internal/stats"); [[ "$code" = 401 || "$code" = 403 || "$code" = 404 ]] && ok "internal не доступен снаружи" || bad "internal снаружи: $code"
echo "ℹ дальше — маршрут судьи из docs/DEMO.md руками"
