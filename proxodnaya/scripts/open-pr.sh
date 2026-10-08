#!/usr/bin/env bash
# Использование (из proxodnaya/): scripts/open-pr.sh FR-03 "feat(access): атомарное гашение токена"
set -euo pipefail
ID="${1:?ID требования, например FR-03}"; TITLE="${2:?заголовок PR}"
ROOT="$(git rev-parse --show-toplevel)"; CASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$CASE"
BR="$(git branch --show-current)"
[[ "$BR" == "main" ]] && { echo "✖ ты в main — создай ветку <type>/$ID-<slug>"; exit 1; }
[[ "$TITLE" == *"$ID"* ]] || TITLE="$(sed -E "s/^([a-z]+(\([^)]*\))?!?: )/\1$ID /" <<<"$TITLE")"
git diff --quiet && git diff --cached --quiet || { echo "✖ есть незакоммиченные изменения"; exit 1; }

git fetch -q origin main && git rebase origin/main
scripts/verify.sh
scripts/guard.sh
git push -u origin "$BR" --force-with-lease

BODY="$(mktemp)"
sed "s/{{ID}}/$ID/g" "$ROOT/.github/PULL_REQUEST_TEMPLATE.md" > "$BODY"
{ echo; echo "### Коммиты"; git log --oneline origin/main..HEAD | sed 's/^/- /'; } >> "$BODY"
if gh pr view "$BR" >/dev/null 2>&1; then
  echo "PR уже существует:"; gh pr view "$BR" --json url -q .url
else
  gh pr create --base main --head "$BR" --title "$TITLE" --body-file "$BODY" --assignee @me
fi
rm -f "$BODY"
echo "✔ Теперь заполни разделы PR (как проверить руками, риски) и позови владельца модуля."
