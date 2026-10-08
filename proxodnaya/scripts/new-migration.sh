#!/usr/bin/env bash
# Использование: scripts/new-migration.sh add_pass_tokens
set -euo pipefail
SLUG="${1:?slug, например add_pass_tokens}"
[[ "$SLUG" =~ ^[a-z0-9_]+$ ]] || { echo "slug: только a-z0-9_"; exit 1; }
CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$CASE/backend/src/main/resources/db/migration"; mkdir -p "$DIR"
F="$DIR/V$(date -u +%Y%m%d%H%M%S)__${SLUG}.sql"
printf -- "-- %s\n-- автор: %s, требование: <FR-xx>\n\n" "$SLUG" "$(git config user.name)" > "$F"
echo "$F"
