#!/usr/bin/env bash
# Handoff для следующей сессии/агента (docs/WORKFLOW.md §2.4).
#   scripts/handoff.sh > /tmp/handoff.md     — заготовка с фактами из git; допиши руками/агентом
#   scripts/handoff.sh --apply /tmp/handoff.md — заменить раздел «## Handoff» в описании PR текущей ветки (gh)
set -euo pipefail
BR="$(git branch --show-current)"
if [ "${1:-}" = "--apply" ]; then
  F="${2:?файл с handoff}"; command -v gh >/dev/null || { echo "✖ нужен gh"; exit 1; }
  OLD="$(mktemp)"; NEW="$(mktemp)"
  gh pr view "$BR" --json body -q .body > "$OLD"
  awk -v f="$F" 'BEGIN{while((getline l<f)>0) h=h l "\n"}
       /^## Handoff/{print h; skip=1; done=1; next} /^## /{skip=0} !skip{print}
       END{if(!done) printf "\n%s", h}' "$OLD" > "$NEW"
  gh pr edit "$BR" --body-file "$NEW" >/dev/null && echo "✔ Handoff обновлён в PR $BR"
  rm -f "$OLD" "$NEW"; exit 0
fi
git fetch -q origin main 2>/dev/null || true
cat <<MD
## Handoff
_Обновлено: $(date '+%Y-%m-%d %H:%M') · ветка \`$BR\` · последний коммит \`$(git rev-parse --short HEAD)\`_

**Состояние:** <!-- одной строкой: что работает, что нет -->

**Сделано (коммиты):**
$(git log --oneline origin/main..HEAD 2>/dev/null | sed 's/^/- /')

**Незакоммичено:** $(git status --porcelain | wc -l | tr -d ' ') файлов

**Следующий шаг:** <!-- конкретно: файл/тест/команда -->

**Подводные камни / почему сделано так:** <!-- то, что не видно из кода -->

**Как проверить:** <!-- команды и шаги в UI -->

**Проверки:** <!-- ci-local quick/full: ок/упало (что именно) -->
MD
