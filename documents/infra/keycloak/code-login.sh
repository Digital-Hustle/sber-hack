#!/usr/bin/env bash
# FE-UI-02: вход «логин → код» для УЖЕ развёрнутого стенда. Новый стенд получает то же из realm-documents.json
# (--import-realm не трогает существующий realm). Повторный запуск безопасен. Запуск из infra/:
#   ./keycloak/code-login.sh
set -euo pipefail
cd "$(dirname "$0")/.."
env_val() { grep "^$1=" .env | cut -d= -f2-; }
kc() { docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080/auth --realm master \
  --user "$(env_val KC_BOOTSTRAP_ADMIN_USERNAME)" --password "$(env_val KC_BOOTSTRAP_ADMIN_PASSWORD)" >/dev/null
R=(-r documents)
F=documents-code

if ! kc get authentication/flows "${R[@]}" --fields alias | jq -e --arg f "$F" 'any(.[]; .alias == $f)' >/dev/null; then
  kc create authentication/flows "${R[@]}" -s alias=$F -s providerId=basic-flow -s topLevel=true -s builtIn=false \
    -s 'description=FE-UI-02: логин, затем код из приложения-аутентификатора; пароль — только пока код не настроен'
  kc create authentication/flows/$F/executions/execution "${R[@]}" -s provider=auth-cookie
  for sub in forms; do kc create authentication/flows/$F/executions/flow "${R[@]}" -s alias=$F-$sub -s type=basic-flow -s provider=registration-page-form; done
  kc create authentication/flows/$F-forms/executions/execution "${R[@]}" -s provider=auth-username-form
  for sub in otp password; do kc create authentication/flows/$F-forms/executions/flow "${R[@]}" -s alias=$F-$sub -s type=basic-flow -s provider=registration-page-form; done
  kc create authentication/flows/$F-otp/executions/execution "${R[@]}" -s provider=conditional-user-configured
  kc create authentication/flows/$F-otp/executions/execution "${R[@]}" -s provider=auth-otp-form
  kc create authentication/flows/$F-password/executions/execution "${R[@]}" -s provider=conditional-sub-flow-executed
  kc create authentication/flows/$F-password/executions/execution "${R[@]}" -s provider=auth-password-form
  # требования: cookie и формы — альтернативы; ветки кода и пароля — условные; остальное обязательно
  kc get authentication/flows/$F/executions "${R[@]}" | jq -c '.[]' | while read -r e; do
    req=$(jq -r 'if .providerId == "auth-cookie" or .displayName == "documents-code-forms" then "ALTERNATIVE"
                 elif (.displayName | IN("documents-code-otp", "documents-code-password")) then "CONDITIONAL" else "REQUIRED" end' <<<"$e")
    jq -c --arg r "$req" '.requirement = $r' <<<"$e" | kc update authentication/flows/$F/executions "${R[@]}" -f -
  done
  cond=$(kc get authentication/flows/$F/executions "${R[@]}" | jq -r '.[] | select(.providerId == "conditional-sub-flow-executed") | .id')
  kc create authentication/executions/"$cond"/config "${R[@]}" -s alias=$F-otp-not-executed \
    -s 'config={"flow_to_check":"documents-code-otp","check_result":"not-executed"}'
fi

kc update realms/documents -s browserFlow=$F -s rememberMe=true \
  -s ssoSessionIdleTimeoutRememberMe=604800 -s ssoSessionMaxLifespanRememberMe=604800

# демо-учётки без кода: при следующем входе (по паролю) Keycloak попросит настроить приложение-аутентификатор
for u in operator senior auditor; do
  id=$(kc get users "${R[@]}" -q exact=true -q username=$u --fields id | jq -r '.[0].id // empty')
  [ -n "$id" ] || continue
  if ! kc get users/"$id"/credentials "${R[@]}" | jq -e 'any(.[]; .type == "otp")' >/dev/null; then
    kc update users/"$id" "${R[@]}" -s 'requiredActions=["CONFIGURE_TOTP"]'
  fi
done
echo "вход по коду включён: поток $F"
