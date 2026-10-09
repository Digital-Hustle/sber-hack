# Скиллы и MCP для агентов — «Документы»

Скиллы — папки с `SKILL.md` в `../../.agents/skills/` (`.claude/skills` — симлинк). Общие с «Проходной», плюс свой процессный скилл **`documents-dev`**.

**Приоритет при конфликте:** `documents/rules.md` → `documents-dev` → `backend-spring` / `frontend-fsd` → сторонние скиллы. Блоки «Проходная» в сторонних скиллах читай как «наш кейс»: те же поправки (motion, токены, лицензии).

## Что брать под роль
| Роль | Обязательно | По ситуации |
|---|---|---|
| **B1** (gateway, intake, common, infra, CI) | `documents-dev`, `backend-spring` | `springboot-security` (закрытость, Д8), `test-driven-development` (проверки приёма), `systematic-debugging`, `verification-before-completion` |
| **B2** (docproc, ocr, eval) | `documents-dev` (+ `references/pipeline.md`), `backend-spring` (+ `references/python-service.md`, `data-messaging.md`) | `test-driven-development` (очередь, валидаторы MRZ/СНИЛС/ИНН), `systematic-debugging` (ошибки OCR), `verification-before-completion` (прогон eval перед «готово») |
| **B3** (review, libs/llm) | `documents-dev` (+ `references/llm.md`), `backend-spring` (+ `frontend-contract.md`: SSE, курсоры, выгрузки) | `test-driven-development` (история, матрица ролей), `springboot-security` |
| **F1** (`/apply`) | `documents-dev` (+ `references/frontend.md`), `frontend-fsd` (§8) | `emil-design-eng`, `animate`, `shadcn`, `webapp-testing` (mobile 390×844) |
| **F2** (`/operator`, shared) | `documents-dev`, `frontend-fsd` (§8) | `shadcn`, `frontend-design`, `design-taste-frontend`, `improve-animations`, `webapp-testing` (1440×900) |
| Все | — | `find-skills`, `verification-before-completion` |

## Установленные скиллы
Полный перечень, источники и лицензии — `../../proxodnaya/docs/AGENT_SKILLS.md` (тот же набор). Свой: `documents-dev` — цикл задачи, конвейер разбора, модель, оператор, безопасность.

## MCP
`shadcn` и `magicui` из `.mcp.json` / `.cursor/mcp.json` / `.codex/config.toml`. Конфиг запускает shadcn из `proxodnaya/frontend`; для нашего фронта запускайте shadcn CLI из `documents/frontend` (`npx shadcn@latest add …`), `components.json` с реестрами — шаблон `.agents/skills/frontend-fsd/assets/components.json`.

## Как добавить скилл
`npx skills find <запрос>` → `npx skills add <owner/repo@skill> -a claude-code --copy -y` → проверить лицензию → строка сюда и в `DECISIONS.md`.
