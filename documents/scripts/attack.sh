#!/usr/bin/env bash
# X-05 «Злоумышленник»: автоматизируемая часть чек-листа I (REQUIREMENTS) и модели угроз T1–T10 (SECURITY §3)
# на живом стенде через публичный вход. Использование: scripts/attack.sh https://host [--docker]
#   --docker — ещё и «рестарт посреди разбора» (docker compose restart docproc), запускать из documents/infra.
# Нужны curl и jq. Создаёт 2 тестовые заявки с синтетикой (eval/synthetic), ПДн не использует.
set -uo pipefail
H="${1:-https://localhost}"; DOCKER="${2:-}"
DIR="$(cd "$(dirname "$0")/.." && pwd)"; SYN="$DIR/eval/synthetic"
API="$H/intake/api/v1/applicant"; TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
fails=0
ok() { echo "✔ $1"; }
bad() { echo "✖ $1"; fails=$((fails + 1)); }
info() { echo "ℹ $1"; }
# check "<что>" "<ожидаемые коды через |>" <фактический>
check() { [[ "|$2|" == *"|$3|"* ]] && ok "$1: $3" || bad "$1: ждали $2, получили $3 $(head -c 300 "$TMP/body" 2>/dev/null)"; }
req() { curl -sk -o "$TMP/body" -D "$TMP/hdr" -w '%{http_code}' "$@"; }
idem() { echo "atk-$RANDOM-$RANDOM-$(date +%s%N)"; }
create() { curl -sk -X POST "$API/applications" -H 'Content-Type: application/json' \
  -d '{"consentPd":true,"contact":{"email":"attack@example.com"}}'; }
upload() { # upload <appId> <key> <file> <имя> [idem]
  req -X POST "$API/applications/$1/documents" -H "Authorization: Applicant $2" \
    -H "Idempotency-Key: ${5:-$(idem)}" -F "file=@$3;filename=$4"; }

echo "— T1/T3: оператор и файлы без входа и с поддельным токеном"
for p in /review/api/v1/applications /intake/api/v1/operator/applications /docproc/api/v1/model/status; do
  check "$p без токена" 401 "$(req "$H$p")"
  check "$p с мусорным токеном" 401 "$(req -H 'Authorization: Bearer garbage' "$H$p")"
  # alg=none: подпись не проверяется только у сломанных библиотек
  none="$(printf '{"alg":"none"}' | base64 | tr -d '=\n' | tr '/+' '_-').$(printf '{"sub":"x","realm_access":{"roles":["SENIOR_OPERATOR"]},"exp":9999999999}' | base64 | tr -d '=\n' | tr '/+' '_-')."
  check "$p с токеном alg=none" 401 "$(req -H "Authorization: Bearer $none" "$H$p")"
done
for p in /intake/internal/intake/files/1 /internal/intake/applications/1/summary /docproc/INTERNAL/x /review/api/../internal/x; do
  check "$p снаружи (даже с токеном)" 404 "$(req -H 'Authorization: Bearer garbage' "$H$p")"
done
for p in /actuator/prometheus /intake/actuator/prometheus /docproc/actuator/prometheus /review/actuator/env; do
  code=$(req "$H$p")
  if grep -qE '^(jvm_|# HELP|\{"activeProfiles)' "$TMP/body"; then bad "$p: метрики/настройки видны снаружи ($code)"; else ok "$p снаружи: метрик и настроек нет ($code)"; fi
done

echo "— T2: чужой ключ заявки (IDOR)"
check "создание без согласия" 400 "$(req -X POST "$API/applications" -H 'Content-Type: application/json' -d '{"consentPd":false}')"
grep -q CONSENT_REQUIRED "$TMP/body" && ok "код CONSENT_REQUIRED" || bad "нет кода CONSENT_REQUIRED"
A="$(create)"; B="$(create)"
AID=$(jq -r .applicationId <<<"$A"); AK=$(jq -r .accessKey <<<"$A"); BID=$(jq -r .applicationId <<<"$B"); BK=$(jq -r .accessKey <<<"$B")
if [[ "$AID" == null || "$BID" == null || -z "$AID" ]]; then bad "заявки не создались: $A"; echo "итог: $fails"; exit 1; fi
ok "созданы заявки A и B"
check "заявка A со своим ключом" 200 "$(req -H "Authorization: Applicant $AK" "$API/applications/$AID")"
jq -e '.. | objects | select(has("fields") or has("value"))' "$TMP/body" >/dev/null 2>&1 \
  && bad "публичная карточка отдаёт значения полей" || ok "публичная карточка без значений полей (минимум ПДн)"
check "заявка A с ключом B" 404 "$(req -H "Authorization: Applicant $BK" "$API/applications/$AID")"
check "заявка A без ключа" "401|404" "$(req "$API/applications/$AID")"
check "заявка A с выдуманным ключом" "401|404" "$(req -H 'Authorization: Applicant AAAAAAAAAAAAAAAAAAAAAAAA' "$API/applications/$AID")"

echo "— T4/T10: файлы"
: > "$TMP/empty.jpg"; head -c 4096 /dev/urandom > "$TMP/noise.pdf"
check "пустой файл" 400 "$(upload "$AID" "$AK" "$TMP/empty.jpg" empty.jpg)"
grep -q FILE_EMPTY "$TMP/body" && ok "код FILE_EMPTY + подсказка" || bad "нет FILE_EMPTY"
check "случайные байты под видом PDF" "415|422" "$(upload "$AID" "$AK" "$TMP/noise.pdf" scan.pdf)"
check "без Idempotency-Key" 400 "$(req -X POST "$API/applications/$AID/documents" -H "Authorization: Applicant $AK" -F "file=@$SYN/syn-001.jpg")"
cp "$SYN/syn-001.jpg" "$TMP/passport.pdf"
check "JPG, переименованный в .pdf (тип по сигнатуре)" 202 "$(upload "$AID" "$AK" "$TMP/passport.pdf" passport.pdf)"
DOC=$(jq -r .documentId "$TMP/body"); FILE=$(jq -r .fileId "$TMP/body")
code=$(upload "$AID" "$AK" "$TMP/passport.pdf" again.pdf)
if [[ "$code" == 409 ]] || { [[ "$code" == 202 ]] && jq -e '.duplicateOf != null' "$TMP/body" >/dev/null; }; then
  ok "повторная загрузка того же файла распознана как дубль ($code)"; else bad "дубль не распознан: $code"; fi
K=$(idem); upload "$AID" "$AK" "$SYN/syn-002.jpg" a.jpg "$K" >/dev/null
c1=$(jq -r .documentId "$TMP/body"); upload "$AID" "$AK" "$SYN/syn-002.jpg" a.jpg "$K" >/dev/null
[[ "$(jq -r .documentId "$TMP/body")" == "$c1" ]] && ok "повтор с тем же Idempotency-Key — тот же документ" || bad "Idempotency-Key не соблюдён"
check "загрузка в заявку A с ключом B" 404 "$(upload "$AID" "$BK" "$SYN/syn-003.jpg" x.jpg)"
check "файл A с ключом B" 404 "$(req -H "Authorization: Applicant $BK" "$H/intake/api/v1/files/$FILE/content")"
check "файл A без входа" "401|403|404" "$(req "$H/intake/api/v1/files/$FILE/content")"
check "файл A со своим ключом" 200 "$(req -H "Authorization: Applicant $AK" "$H/intake/api/v1/files/$FILE/content")"
grep -qi '^cache-control:.*no-store' "$TMP/hdr" && ok "файл отдаётся с Cache-Control: no-store" || info "нет Cache-Control: no-store у файла"

echo "— Асинхронный разбор: загрузка отвечает сразу, стадии идут сами"
stage_of() { curl -sk -H "Authorization: Applicant $AK" "$API/applications/$AID" | jq -r --arg d "$1" '.documents[] | select(.id==$d) | .stage'; }
seen=""; for _ in $(seq 1 45); do s=$(stage_of "$DOC"); [[ "$seen" == *"$s"* ]] || seen="$seen $s"; [[ "$s" != RECEIVED && "$s" != QUEUED ]] && break; sleep 2; done
[[ "$s" != RECEIVED && -n "$s" ]] && ok "стадии документа:$seen" || bad "документ застрял в $s"
# X-07: OCR должен отработать — документ уходит дальше EXTRACTING_TEXT и не падает в FAILED (раньше h2c ломал OCR).
for _ in $(seq 1 90); do s=$(stage_of "$DOC"); [[ "$seen" == *"$s"* ]] || seen="$seen $s"
  [[ "$s" != RECEIVED && "$s" != QUEUED && "$s" != EXTRACTING_TEXT && -n "$s" ]] && break; sleep 2; done
[[ "$s" != RECEIVED && "$s" != QUEUED && "$s" != EXTRACTING_TEXT && "$s" != FAILED && -n "$s" ]] \
  && ok "текст извлечён, документ дальше EXTRACTING_TEXT:$seen" || bad "документ не прошёл OCR: $s ($seen)"
[[ "$s" == WAITING_MODEL ]] && info "модель недоступна → WAITING_MODEL (не «готово») — так и должно быть без модели"

if [[ "$DOCKER" == --docker ]]; then
  echo "— Рестарт docproc посреди разбора"
  cp "$SYN/syn-004.jpg" "$TMP/r.jpg"; upload "$AID" "$AK" "$TMP/r.jpg" r.jpg >/dev/null; RD=$(jq -r .documentId "$TMP/body")
  docker compose restart docproc >/dev/null 2>&1 && ok "docproc перезапущен"
  for _ in $(seq 1 90); do s=$(stage_of "$RD"); [[ "$s" != RECEIVED && "$s" != QUEUED && -n "$s" ]] && break; sleep 2; done
  [[ "$s" != RECEIVED && "$s" != QUEUED && -n "$s" ]] && ok "после рестарта документ дошёл до $s" || bad "после рестарта документ застрял в $s"
fi

echo "— T8: подбор кода возврата"
last=""; for i in $(seq 1 7); do
  last=$(req -X POST "$API/resume" -H 'Content-Type: application/json' -d '{"phoneOrEmail":"attack@example.com","resumeCode":"ZZZZZZ"}')
  [[ "$last" == 429 ]] && break; done
check "после серии неверных кодов" 429 "$last"
grep -qi '^retry-after:' "$TMP/hdr" && ok "429 с Retry-After" || bad "429 без Retry-After"

echo "— Заголовки"
curl -sk -D "$TMP/h" -o /dev/null "$H/apply"
for h in strict-transport-security content-security-policy x-content-type-options referrer-policy; do
  grep -qi "^$h:" "$TMP/h" && ok "заголовок $h" || bad "нет заголовка $h"; done
c=$(req -X OPTIONS "$H/review/api/v1/applications" -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: GET')
grep -qi '^access-control-allow-origin:' "$TMP/hdr" && bad "CORS пускает чужой origin" || ok "CORS чужому origin отказывает ($c)"

echo; [[ $fails == 0 ]] && echo "итог: всё закрыто" || echo "итог: проблем — $fails"
exit $(( fails > 0 ))
