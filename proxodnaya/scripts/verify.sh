#!/usr/bin/env bash
# Запускает проверки только для затронутых частей монорепы.
# Использование: scripts/verify.sh [backend|frontend|contract|infra|all]...
set -euo pipefail
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$CASE"
BASE="${VERIFY_BASE:-origin/main}"

parts=("$@")
if [ ${#parts[@]} -eq 0 ]; then
  git rev-parse --verify -q "$BASE" >/dev/null || BASE="$(git rev-list --max-parents=0 HEAD | tail -1)"
  changed="$( { git diff --relative --name-only "$BASE"...HEAD; git diff --relative --name-only; git diff --relative --name-only --cached; git ls-files --others --exclude-standard; } | sort -u)"
  grep -q '^backend/'  <<<"$changed" && parts+=(backend contract)
  grep -q '^frontend/' <<<"$changed" && parts+=(frontend)
  grep -q '^contracts/' <<<"$changed" && parts+=(contract frontend)
  grep -q '^infra/'    <<<"$changed" && parts+=(infra)
  [ ${#parts[@]} -eq 0 ] && { echo "verify: нет изменений в коде (только docs?) — ок"; exit 0; }
fi
[ "${parts[0]}" = "all" ] && parts=(backend contract frontend infra)
mapfile -t parts < <(printf '%s\n' "${parts[@]}" | awk '!seen[$0]++')

run() { echo "▶ $*"; "$@"; }
for p in "${parts[@]}"; do
  case "$p" in
    backend)  [ -d backend ]  && (cd backend && run ./mvnw -q -B verify) ;;
    contract) if [ -d backend ]; then
                (cd backend && run ./mvnw -q -B -DskipTests verify -Pcontract)
                git diff --exit-code -- contracts/openapi.json \
                  || { echo "✖ contracts/openapi.json устарел — закоммить перегенерированный файл"; exit 1; }
              fi ;;
    frontend) [ -d frontend ] && (cd frontend && run pnpm install --frozen-lockfile --silent && run pnpm gen:api \
                && run pnpm -r lint && run pnpm -r typecheck && run pnpm -r build) ;;
    infra)    [ -f infra/docker-compose.yml ] && (cd infra && run docker compose --env-file .env.example config -q) ;;
    *) echo "неизвестная часть: $p"; exit 2 ;;
  esac
done
echo "✔ verify: ${parts[*]}"
