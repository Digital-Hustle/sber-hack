#!/usr/bin/env bash
# Быстрый смоук стенда «Документы». Использование: scripts/smoke.sh https://host
# Actuator сервисов живёт на management-порту внутри сети — снаружи проверяем маршруты gateway.
set -euo pipefail
H="${1:-https://localhost}"; K="${KEYCLOAK_URL:-${H/\/\//\/\/auth.}}"
GW_HEALTH="${GATEWAY_HEALTH_URL:-$H/actuator/health}"
ok() { echo "✔ $1"; } ; bad() { echo "✖ $1"; exit 1; }
code_of() { curl -sk -o /dev/null -w '%{http_code}' "$@"; }

curl -fsSk "$GW_HEALTH" | grep -q '"UP"' && ok "gateway health" || bad "gateway health ($GW_HEALTH)"
curl -fsSk "$K/realms/documents/.well-known/openid-configuration" >/dev/null && ok "keycloak realm documents" || bad "keycloak"

# Одно SPA: зоны /apply (соискатель) и /operator (оператор) отдают тот же index.html
for zone in apply operator; do
  curl -fsSk "$H/$zone" | grep -qi '<div id="root"' && ok "frontend /$zone" || bad "frontend /$zone"
done

# Д8: рабочее место оператора закрыто — без токена 401, а не данные/5xx/404
for p in /review/api/v1/ping /review/api/v1/applications /intake/api/v1/operator/applications /docproc/api/v1/ping; do
  c=$(code_of "$H$p"); [ "$c" = 401 ] && ok "$p без входа: 401" || bad "$p без входа вернул $c"
done
# Файлы с ПДн по прямой ссылке без входа не открываются
c=$(code_of "$H/intake/api/v1/files/00000000-0000-0000-0000-000000000000/content")
[[ "$c" = 401 || "$c" = 403 ]] && ok "файл по прямой ссылке без входа: $c" || bad "файл по прямой ссылке без входа: $c"
# Заявка соискателя без ключа заявки недоступна
c=$(code_of "$H/intake/api/v1/applicant/applications/00000000-0000-0000-0000-000000000000")
[[ "$c" = 401 || "$c" = 403 || "$c" = 404 ]] && ok "чужая заявка без ключа: $c" || bad "чужая заявка без ключа: $c"

# Внутренние ручки и ocr наружу не торчат
for p in /internal/intake/files /intake/internal/files /ocr/v1/extract /docproc/internal/jobs; do
  c=$(code_of "$H$p"); [[ "$c" = 401 || "$c" = 403 || "$c" = 404 ]] && ok "$p закрыт снаружи ($c)" || bad "$p снаружи: $c"
done
echo "ℹ дальше — маршрут судьи из docs/DEMO.md руками"
