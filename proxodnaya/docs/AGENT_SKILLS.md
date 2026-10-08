# Скиллы для агентов

Скиллы — папки с `SKILL.md` (открытый стандарт Agent Skills). Их понимают Claude Code (`.claude/skills/`), Codex (`.agents/skills/`), Cursor, Gemini CLI и другие. У нас канонический путь — `.agents/skills/` в корне репозитория, а `.claude/skills` — симлинк на него.

## Наш скилл (обязательный)
**`proxodnaya-dev`** — полный цикл работы в кейсе «Проходная»: задача → ветка → код в границах модуля → `verify.sh` + `guard.sh` → PR по шаблону → ревью → CI → деплой. Справочники по backend, frontend, безопасности, LLM, CI и PR лежат в `references/` и подгружаются по необходимости.

## Сторонние скиллы, которые стоит поставить
Копируйте только нужные папки в `.agents/skills/`. Перед добавлением проверьте лицензию (у этих — MIT / Apache-2.0) и запишите в DECISIONS.md.

| Скилл | Откуда | Зачем нам | Кому |
|---|---|---|---|
| `test-driven-development` | github.com/obra/superpowers | red-green для DecisionEngine, токенов, валидатора | B1, B2, B3 |
| `systematic-debugging` | obra/superpowers | разбор падений CI и багов интеграции без «угадывания» | все |
| `verification-before-completion` | obra/superpowers | агент не говорит «готово», пока не проверил | все |
| `requesting-code-review` / `receiving-code-review` | obra/superpowers | качество PR и ответы на ревью | все |
| `using-git-worktrees` | obra/superpowers | несколько агентов на одной машине без конфликтов | все |
| `webapp-testing` | github.com/anthropics/skills | Playwright-проверка UI киоска/админки локально | F1, F2 |
| `frontend-design` | anthropics/skills | аккуратный UI без «дефолтного» вида | F1, F2 |
| `skill-creator` | anthropics/skills | доработка нашего скилла по ходу дня | B1 |

**Не ставить** на хакатон: `brainstorming`, `writing-plans`, `subagent-driven-development` из superpowers — они замедляют агента вопросами и планами, а план у нас уже есть.

## Как добавить
```bash
git clone --depth 1 https://github.com/obra/superpowers /tmp/sp
cp -r /tmp/sp/skills/{test-driven-development,systematic-debugging,verification-before-completion,using-git-worktrees} .agents/skills/
git clone --depth 1 https://github.com/anthropics/skills /tmp/as
cp -r /tmp/as/skills/webapp-testing .agents/skills/
```
