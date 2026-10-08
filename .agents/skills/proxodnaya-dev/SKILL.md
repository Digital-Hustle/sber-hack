---
name: proxodnaya-dev
description: Use for ANY change in the «Проходная» case (folder proxodnaya/ of the sber-hack monorepo) (backend Java/Spring, frontend React FSD app with zones kiosk/worker/admin, Python face service, infra, Keycloak, LLM assistant, docs, CI). Covers picking a task, branching, implementing within module boundaries, running checks, writing Liquibase migrations, updating the API contract, opening and reviewing pull requests, fixing CI, and deploying. Trigger on requests like "сделай FR-12", "добавь эндпоинт", "почини CI", "открой PR", "сделай ревью PR", "добавь миграцию", "задеплой".
---

# proxodnaya-dev

Кейс «Проходная» в монорепе `sber-hack`. **Все пути ниже — относительно папки `proxodnaya/`**; команды запускай из неё (`cd proxodnaya`). `.github/` и `.agents/` лежат в корне репозитория. Судьи проверяют руками, есть условия снятия с конкурса. Любая задача идёт по циклу ниже.

## 0. Перед стартом (каждая сессия)
1. Прочитай `AGENTS.md` и `rules.md`. Красные линии `rules.md §1` нарушать нельзя ни при каких условиях.
2. Определи модуль и владельца по таблице в `AGENTS.md`. Чужой сервис трогай только через его API/события или с согласия владельца.
3. `git fetch && git switch main && git pull --rebase`.

## 1. Задача → ветка
- У задачи должен быть ID требования (`FR-xx`, `NFR-xx`, `B-xx`) из `docs/REQUIREMENTS.md`. Если ID нет — спроси человека, не придумывай.
- Ветка: `<type>/<ID>-<slug>`, например `feat/FR-03-qr-consume`. Типы: feat, fix, refactor, test, docs, ci, chore.
- Для параллельной работы нескольких агентов на одной машине: `git worktree add ../gate-<slug> -b <branch>`.

## 2. Реализация
Прочитай нужный reference — только его, не все:
| Область | Файл |
|---|---|
| Backend: общие правила Spring/Gradle/jOOQ/Liquibase/Kafka/тесты | скилл `backend-spring` |
| Backend: сервисы кейса, инварианты access/face, обязательные тесты | `references/backend.md` |
| Frontend: общие правила FSD | скилл `frontend-fsd` |
| Frontend: бренд Сбера, анимации Motion, кастомные контролы, каталог UI-библиотек и MCP | скилл `frontend-fsd` §8 → `references/sber-brand.md`, `motion.md`, `custom-controls.md`, `component-catalog.md` |
| Backend ↔ Frontend: курсоры, options, SSE, `checks[]`, idempotency | `backend-spring/references/frontend-contract.md` |
| Какие ещё скиллы брать под роль (TDD, отладка, анимации, Playwright) | `proxodnaya/docs/AGENT_SKILLS.md` |
| Frontend: зоны kiosk/worker/admin, ключ устройства, камера | `references/frontend.md` |
| Keycloak, роли, токены, ключи устройств, шифрование, аудит | `references/security.md` |
| LLM-слой, помощник, валидатор чисел | `references/llm-assistant.md` |
| CI, docker compose, деплой | `references/ci-deploy.md` |
| Как оформить PR и делать ревью | `references/pr-workflow.md` |

Правила реализации:
- Сначала контракт: меняешь REST или событие → обнови `docs/API.md`, `contracts/openapi/<svc>.json` (`scripts/verify.sh contract`) или `contracts/events/`, и всех потребителей.
- Новая миграция только через `scripts/new-migration.sh <svc> <slug>` (Liquibase formatted SQL, имя с меткой времени — без коллизий у параллельных PR).
- Пиши тест вместе с кодом. Для движка решений и безопасности — сначала тест (red → green).
- Никаких моков, заглушек, «TODO: потом» и заготовленных ответов в боевом пути. Если что-то не готово — feature-флаг `false` и честное сообщение в UI.
- Маленькие коммиты в стиле Conventional Commits со ссылкой на ID: `feat(access): FR-03 атомарное гашение QR`.

## 3. Проверка (обязательно до PR)
```bash
scripts/verify.sh          # сам определяет затронутые части и гоняет нужные проверки
scripts/guard.sh           # сканер красных линий: секреты, решение на клиенте, in-memory хранилища, заглушки LLM
```
Не открывай PR, пока оба зелёные. Если проверка падает не из-за тебя — напиши об этом в PR, но не отключай проверку.

## 4. Pull request
```bash
scripts/open-pr.sh "<ID>" "<короткий заголовок>"   # rebase на main, verify, guard, push, gh pr create по шаблону
```
- Один PR = одно требование, ≤ ~400 строк диффа без сгенерированных файлов.
- Заполни шаблон честно: что сделано, как проверить руками через UI, какие риски.
- Агент не мержит и не апрувит свои PR. Мерж делает человек-владелец после зелёного CI.

## 5. Ревью чужого PR (по просьбе человека)
`gh pr checkout <n>` → `scripts/verify.sh` → `scripts/guard.sh` → пройди чек-лист из `references/pr-workflow.md` → оставь комментарии через `gh pr review --comment` (не `--approve`, если тебя не попросили явно).

## 6. Отчёт человеку (в конце каждой задачи)
- Что сделано (ID), файлы, ссылка на PR.
- Как проверить руками: шаги в UI.
- **3–5 строк «как это работает» — чтобы владелец мог объяснить судьям** (условие Д11).
- Что осталось или какие риски.

## Чего не делать никогда
- Не коммить `.env`, ключи, токены, дампы БД, фото, модели.
- Не редактировать уже смерженные changeset-ы Liquibase.
- Не отключать тесты, проверки CI, ArchUnit, CSP ради «чтобы прошло».
- Не принимать решение о проходе во фронтенде, не хранить данные в памяти процесса, не возвращать заготовленный ответ модели.
- Не ходить в чужую БД, не публиковать события мимо outbox, не выносить гашение QR (`qr_uses`) из транзакции access.
- Не делать force-push в `main`, не мержить без CI.
