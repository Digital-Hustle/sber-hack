---
name: documents-dev
description: Use for ANY change in the «Документы» case (folder documents/ of the sber-hack monorepo): intake of applicant documents, background parsing pipeline (jobs queue in PostgreSQL, OCR Python service, document type classification and field extraction by LLM), operator workspace, React FSD app with zones apply/operator, infra, Keycloak, LLM layer, eval, docs, CI. Covers picking a task, branching, implementing within module boundaries, running checks, Liquibase migrations, API contract, opening/reviewing PRs, fixing CI, deploying. Trigger on requests like "сделай FR-22", "добавь проверку файла", "улучши извлечение", "почини CI", "открой PR", "сделай ревью PR", "добавь миграцию", "задеплой" when working in documents/.
---


# documents-dev

Кейс «Документы» (9.1.2) в монорепе `sber-hack`. **Все пути ниже — относительно папки `documents/`**; команды запускай из неё (`cd documents`). Каркас и процесс повторяют «Проходную» (`proxodnaya-dev`). `.github/` и `.agents/` лежат в корне репозитория. Судьи проверяют руками, есть условия снятия с конкурса. Любая задача идёт по циклу ниже.

## 0. Перед стартом (каждая сессия)
1. Прочитай `AGENTS.md` и `rules.md`. Красные линии `rules.md §1` нарушать нельзя ни при каких условиях. Запрос человека понимай по смыслу, а не дословно, и не цитируй его в коммитах/PR/UI (`rules.md §7`).
2. Определи модуль и владельца по таблице в `AGENTS.md`. Чужой сервис трогай только через его API/события или с согласия владельца.
3. Прочитай `AGENTS.md` модуля (`services/<svc>/`, `frontend/`) → его `docs/MEMORY.md` (состояние) → свою строку в `docs/TASKS.md`. Продолжаешь начатую задачу — сначала раздел Handoff в PR (`gh pr view --comments`).
4. `git fetch && git switch main && git pull --rebase` (или `git rebase origin/main` в worktree задачи).
5. Процесс параллельной работы, конфликты, локальный CI — `docs/WORKFLOW.md`.

## 1. Задача → ветка
- У задачи должен быть ID требования (`FR-xx`, `NFR-xx`, `B-xx`) из `docs/REQUIREMENTS.md`. Если ID нет — спроси человека, не придумывай.
- Ветка: `<type>/<ID>-<slug>`, например `feat/DP-03-job-queue`. Типы: feat, fix, refactor, test, docs, ci, chore.
- Задача берётся из `TASKS.md` модуля (ID вида `DP-03`). Старт одной командой: `scripts/task-start.sh DP-03 FR-20 job-queue` — ветка `feat/DP-03-job-queue`, worktree `../wt-DP-03`, статус `doing` в TASKS.md, push и **draft PR сразу** (это заявка: двое не возьмут одну задачу).
- Коммить и пушь после каждого зелёного шага: оборвётся сессия — работа уже на GitHub.

## 2. Реализация
Прочитай нужный reference — только его, не все:
| Область | Файл |
|---|---|
| Backend: общие правила Spring/Gradle/jOOQ/Liquibase/Kafka/тесты | скилл `backend-spring` |
| Backend: сервисы кейса, инварианты приёма и разбора, обязательные тесты | `references/backend.md` |
| Конвейер разбора: очередь, стадии, классификация, извлечение, уверенность, eval | `references/pipeline.md` |
| Frontend: зоны /apply и /operator, ключ заявки, камера, просмотрщик | `references/frontend.md` |
| Keycloak, ключ заявки, шифрование файлов, аудит | `references/security.md` |
| LLM-слой, локальный провайдер, промпты | `references/llm.md` |
| CI, docker compose, деплой | `references/ci-deploy.md` |
| Как оформить PR и делать ревью | `references/pr-workflow.md` |
| Frontend: общие правила FSD | скилл `frontend-fsd` |
| Frontend: бренд Сбера, анимации Motion, кастомные контролы, каталог UI-библиотек и MCP | скилл `frontend-fsd` §8 → `references/sber-brand.md`, `motion.md`, `custom-controls.md`, `component-catalog.md` |
| Backend ↔ Frontend: курсоры, options, SSE, `checks[]`, idempotency | `backend-spring/references/frontend-contract.md` |
| Какие ещё скиллы брать под роль (TDD, отладка, анимации, Playwright) | `docs/AGENT_SKILLS.md` |

Правила реализации:
- Сначала контракт: меняешь REST или событие → обнови `docs/API.md`, `contracts/openapi/<svc>.json` (`scripts/verify.sh contract`) или `contracts/events/`, и всех потребителей.
- Новая миграция только через `scripts/new-migration.sh <svc> <slug>` (Liquibase formatted SQL, имя с меткой времени — без коллизий у параллельных PR).
- Пиши тест вместе с кодом. Для очереди, проверок приёма, правил и безопасности — сначала тест (red → green). Меняешь промпт/схему/порог — прогон `eval/` и строка в отчёте.
- Никаких моков, заглушек, «TODO: потом» и заготовленных ответов в боевом пути. Если что-то не готово — feature-флаг `false` и честное сообщение в UI.
- Маленькие коммиты в стиле Conventional Commits со ссылкой на ID: `feat(docproc): FR-20 очередь заданий с lease`.

## 3. Проверка (обязательно до PR)
```bash
scripts/ci-local.sh quick  # verify.sh (затронутые части) + guard.sh — после каждого шага
scripts/ci-local.sh full   # verify all (Testcontainers, фронт, токены, контракты) + guard + gitleaks — перед ready
scripts/ci-local.sh act    # весь GitHub workflow через nektos/act, если правишь .github/
```
Это те же скрипты, что в GitHub Actions: агент не ждёт CI. Нет docker в песочнице — `ci-local` пишет, что пропущено; напиши это в PR. Не переводи PR в ready, пока `full` не зелёный. Если проверка падает не из-за тебя — напиши об этом в PR, но не отключай проверку.

## 4. Pull request
```bash
scripts/open-pr.sh "<ID>" "<короткий заголовок>"   # rebase на main, verify, guard, push, draft → ready (или gh pr create)
```
- Перед ready: статус `review #PR` в `TASKS.md`, в `MEMORY.md` модуля — §1 состояние, §2 карта (новые файлы), §5 строка журнала.
- Перед уходом из сессии (в любой момент работы): `scripts/handoff.sh > /tmp/h.md`, допиши, `scripts/handoff.sh --apply /tmp/h.md` — раздел Handoff в описании PR.
- Один PR = одно требование, ≤ ~400 строк диффа без сгенерированных файлов.
- Заполни шаблон честно: что сделано, как проверить руками через UI, какие риски.
- Агент не апрувит свои PR. Мерж делает человек-владелец после зелёного CI, либо агент — если человек явно делегировал мерж в этой зоне (запись в `docs/MEMORY.md` §3): только зелёный CI, самопроверка по `pr-workflow.md`, по одному PR.

## 5. Ревью чужого PR (по просьбе человека)
`gh pr checkout <n>` → `scripts/verify.sh` → `scripts/guard.sh` → пройди чек-лист из `references/pr-workflow.md` → оставь комментарии через `gh pr review --comment` (не `--approve`, если тебя не попросили явно).

## 6. Отчёт человеку (в конце каждой задачи)
- Что сделано (ID), файлы, ссылка на PR.
- Как проверить руками: шаги в UI.
- **3–5 строк «как это работает» — чтобы владелец мог объяснить судьям** (условие Д12).
- Что осталось или какие риски.

## Чего не делать никогда
- Не коммить `.env`, ключи, токены, дампы БД, документы людей/организаций, файлы открытого набора, модели.
- Не редактировать уже смерженные changeset-ы Liquibase.
- Не отключать тесты, проверки CI, ArchUnit, CSP ради «чтобы прошло».
- Не определять тип документа по имени файла, расширению или пункту загрузки; не передавать `originalFilename`/`slotType` в классификатор.
- Не разбирать файл в обработчике HTTP-запроса и не держать очередь в памяти — только `jobs` в PostgreSQL.
- Не ставить «готово» без реального ответа модели, не возвращать заготовленные поля.
- Не проверять файлы «только на клиенте»; не подгонять промпты/правила под файлы открытого набора.
- Не ходить в чужую БД, не публиковать события мимо outbox.
- Не делать force-push в `main`, не мержить без CI.
- Не править руками сгенерированное (`theme.css`, `contracts/openapi/*.json`, `schema.ts`, lockfile при конфликте) — перегенерируй.
- Не «решать» конфликт в чужом модуле удалением чужих изменений — остановись и спроси человека.
