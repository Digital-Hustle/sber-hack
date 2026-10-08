#!/usr/bin/env bash
# Использование: scripts/new-migration.sh <service> <slug>   например: scripts/new-migration.sh access add_pass_tokens
set -euo pipefail
SVC="${1:?сервис: people|access|face|shift|assistant|notify}"; SLUG="${2:?slug, например add_pass_tokens}"
[[ "$SLUG" =~ ^[a-z0-9_]+$ ]] || { echo "slug: только a-z0-9_"; exit 1; }
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -d "$CASE/services/$SVC" ] || { echo "нет сервиса services/$SVC"; exit 1; }
DIR="$CASE/services/$SVC/src/main/resources/db/migration"; mkdir -p "$DIR"
F="$DIR/V$(date -u +%Y%m%d%H%M%S)__${SLUG}.sql"
printf -- "-- %s/%s\n-- автор: %s, требование: <FR-xx>\n\n" "$SVC" "$SLUG" "$(git config user.name)" > "$F"
echo "$F"
