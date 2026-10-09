#!/usr/bin/env bash
# Быстрый смоук стенда. Использование: scripts/smoke.sh https://host
# Actuator сервисов живёт на management-порту внутри сети — снаружи проверяем маршруты gateway.
set -euo pipefail
H="${1:-https://localhost}"; K="${KEYCLOAK_URL:-$H/auth}"
GW_HEALTH="${GATEWAY_HEALTH_URL:-$H/actuator/health}"
ok() { echo "✔ $1"; } ; bad() { echo "✖ $1"; exit 1; }
code_of() { curl -sk -o /dev/null -w '%{http_code}' "$@"; }

curl -fsSk "$GW_HEALTH" | grep -q '"UP"' && ok "gateway health" || bad "gateway health ($GW_HEALTH)"
curl -fsSk "$K/realms/proxodnaya/.well-known/openid-configuration" >/dev/null && ok "keycloak realm proxodnaya" || bad "keycloak"

# Одно SPA: зоны /admin, /kiosk, /worker отдают тот же index.html
for zone in admin kiosk worker; do
  curl -fsSk "$H/$zone" | grep -qi '<div id="root"' && ok "frontend /$zone" || bad "frontend /$zone"
done

# Каждый сервис доступен через gateway и закрыт без токена (401, а не 5xx/404)
for svc in people access shift assistant; do
  c=$(code_of "$H/$svc/api/v1/ping")
  [ "$c" = 401 ] && ok "$svc: маршрут есть, без токена 401" || bad "$svc: без токена вернул $c"
done

# Внутренние ручки и face наружу не торчат
for p in /internal/access/stats /access/internal/stats /face/internal/v1/match /internal/v1/match; do
  c=$(code_of "$H$p"); [[ "$c" = 401 || "$c" = 403 || "$c" = 404 ]] && ok "$p закрыт снаружи ($c)" || bad "$p снаружи: $c"
done
echo "ℹ дальше — маршрут судьи из docs/DEMO.md руками"
