# Скиллы и MCP для агентов

Скиллы — папки с `SKILL.md` (открытый стандарт Agent Skills). Их понимают Claude Code, Codex, Cursor, Gemini CLI и другие. Канонический путь — `.agents/skills/` в корне репозитория, `.claude/skills` — симлинк на него.

**Приоритет при конфликте:** `proxodnaya/rules.md` → `proxodnaya-dev` → `backend-spring` / `frontend-fsd` → сторонние скиллы. В каждом стороннем скилле после frontmatter есть блок «Проходная» с поправками, а в `UPSTREAM.md` — источник, коммит и лицензия.

## Что брать под роль
| Роль | Обязательно | По ситуации |
|---|---|---|
| **B1** (gateway, people, common, infra, CI) | `proxodnaya-dev`, `backend-spring` | `springboot-security`, `test-driven-development`, `systematic-debugging`, `verification-before-completion` |
| **B2** (access, face) | `proxodnaya-dev`, `backend-spring` (+ `references/python-service.md`) | `test-driven-development` (DecisionEngine, гашение QR), `systematic-debugging`, `springboot-security` |
| **B3** (shift, assistant) | `proxodnaya-dev`, `backend-spring` (+ `references/frontend-contract.md`: SSE, аналитика) | `test-driven-development` (интервалы, валидатор чисел), `verification-before-completion` |
| **F1** (kiosk, worker) | `proxodnaya-dev`, `frontend-fsd` (§8: бренд, motion, контролы) | `animate`, `review-animations`, `emil-design-eng`, `shadcn`, `webapp-testing` (mobile 390×844) |
| **F2** (admin, shared, entities) | `proxodnaya-dev`, `frontend-fsd` (§8) | `shadcn`, `frontend-design`, `design-taste-frontend` (логин, дашборд, демо), `improve-animations`, `find-animation-opportunities`, `webapp-testing` |
| Все | — | `find-skills` (поиск новых скиллов, правила установки внутри), `verification-before-completion` |

## Установленные скиллы
| Скилл | Источник | Лицензия | Зачем |
|---|---|---|---|
| `proxodnaya-dev`, `backend-spring`, `frontend-fsd` | наши | — | процесс, бэкенд, фронт (FSD + дизайн-система Сбера + Motion) |
| `shadcn` | shadcn-ui/ui | MIT | CLI, реестры, `add/view/search`, композиция компонентов |
| `frontend-design` | anthropics/skills | Apache-2.0 | визуальный уровень без «дефолтного» вида |
| `design-taste-frontend` | leonxlnx/taste-skill | MIT | анти-шаблонный дизайн лендинга, логина, демо (GSAP-части заменены на motion) |
| `emil-design-eng`, `animate`, `review-animations`, `improve-animations`, `find-animation-opportunities` | emilkowalski/skills | MIT | качество движения: когда, чем, как долго и как ревьюить |
| `webapp-testing` | anthropics/skills | Apache-2.0 | Playwright-проверка UI локально |
| `test-driven-development`, `systematic-debugging`, `verification-before-completion` | obra/superpowers | MIT | red-green, отладка без угадывания, «готово» только после проверки |
| `springboot-security` | affaan-m/ecc | MIT | чек-лист Spring Security (поверх нашего SECURITY.md) |
| `find-skills` | vercel-labs/skills | MIT | `npx skills find <запрос>`, установка в репо |

**Не ставим:** `brainstorming`, `writing-plans`, `subagent-driven-development` (замедляют вопросами, план уже есть). `springboot-tdd` и `springboot-verification` из ecc — завязаны на Maven и JPA, у нас Gradle и jOOQ. Скиллы с GSAP-рецептами без поправок.

## MCP-серверы (подключены в репо)
| Сервер | Конфиг | Что даёт | Ключ |
|---|---|---|---|
| `shadcn` | `.mcp.json`, `.cursor/mcp.json`, `.codex/config.toml` | поиск, просмотр и команды `add` по всем реестрам из `proxodnaya/frontend/components.json` (Magic UI, Aceternity, React Bits, Animate UI, Motion Primitives, Eldora, Cult UI, Smooth UI, Evil Charts, Lucide Animated) | не нужен |
| `magicui` | то же | каталог и код Magic UI | не нужен |
| `21st` | то же | поиск по 21st.dev, `get_inspiration`, `search_logo`; `generate` — только с платным AI | `API_KEY_21ST` в окружении (ключ на 21st.dev/mcp). **Не в git** |

Запуск: Claude Code подхватывает `.mcp.json` сам (подтвердить серверы при первом старте). Cursor — `.cursor/mcp.json`. Codex — `.codex/config.toml` (если версия не читает проектный конфиг, скопируйте в `~/.codex/config.toml`). Шаблон `components.json` с реестрами — `.agents/skills/frontend-fsd/assets/components.json`, кладётся в `proxodnaya/frontend/` при создании фронта.

Не подключены: `uiverse-mcp` (неофициальный, 1★), WebMCP Designeer (работает только в браузере). Uiverse и Designeer используем руками, правила — `frontend-fsd/references/component-catalog.md`.

## Как добавить скилл
```bash
npx skills find <запрос>                         # поиск по skills.sh
npx skills add <owner/repo@skill> -a claude-code --copy -y   # в .claude/skills = .agents/skills
```
Дальше: проверить лицензию (MIT/Apache/BSD/ISC), дописать блок «Проходная» и `UPSTREAM.md`, добавить строку сюда и в таблицу лицензий `DECISIONS.md`.
