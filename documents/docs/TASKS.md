# TASKS — межмодульные задачи и вехи (владелец B1)

Задачи модулей — в их `TASKS.md`:
[gateway](../services/gateway/docs/TASKS.md) · [intake](../services/intake/docs/TASKS.md) · [docproc](../services/docproc/docs/TASKS.md) · [ocr](../services/ocr/docs/TASKS.md) · [review](../services/review/docs/TASKS.md) · [libs/llm](../services/libs/llm/docs/TASKS.md) · [frontend](../frontend/docs/TASKS.md).
Здесь — платформа (B1) и задачи для двоих и больше. Формат и статусы — `WORKFLOW.md §2`. Кто что делает сейчас — открытые draft PR (`gh pr list --draft`).

## Вехи (PLAN.md)
| Веха | Когда | Условие готовности | Статус |
|---|---|---|---|
| M0 Каркас | Т+0:45 | `init-backend.sh services` + сервисы собираются, compose поднимает pg/kafka/keycloak/ollama, фронт-каркас с токенами, CI зелёный | done (#12, #27) |
| M1 Контракты | Т+1:00 | API.md, `/internal/**`, `contracts/events/`, схемы 8 типов в DOC-TYPES согласованы | done #40 (check-contracts.py) |
| M2 Интеграция №1, тег `docs-demo-1` | Т+3:00 | анкета → загрузка → стадии → поля по модели → оператор видит карточку; рестарт docproc посреди разбора — без потерь | частично: путь и рестарт проверяет smoke + attack.sh; разбор реальной моделью на стенде не подтверждён (X-04), тег не ставился |
| M3 Интеграция №2, тег `docs-demo-2` | Т+5:00 | 8 видов на открытом наборе (отчёт eval), сбой модели → WAITING_MODEL, правки и история, закрытость по чек-листу I; код-фриз P0/P1 | todo |
| M4 Финал, тег `docs-demo-final` | Т+6:30 | docs, DECISIONS, EVAL-отчёт, слайды, 2 репетиции | todo |

## Платформа (B1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| PLT-00 | — | Получить текст кейса: сверить С1–С22, разд. 5.3/5.5 с REQUIREMENTS, DOC-TYPES, DEMO; поправить гипотезы | B1 + все | blocked: положение (23.09) сверено — Д1–Д12, блоки баллов, 8 бонусов и основания снятия совпадают с DEFENSE.md; текста сценариев С1–С22 в нём нет — гипотезы в REQUIREMENTS/QUESTIONS |
| PLT-01 | NFR-15 | `init-backend.sh services` (кейс documents), `libs/common` — перенести из `proxodnaya/services/libs/common` (sed `proxodnaya` → `documents`), `libs/llm` — каркас библиотеки | B1 | done (до #27): services/, libs/common, libs/llm |
| PLT-02 | NFR-01/02 | `infra/docker-compose.yml`: postgres (БД+роль на сервис, volume), kafka + `topics.sh`, keycloak (realm из файла), jaeger, caddy, ollama + ollama-pull (профиль `llm-local`) | B1 | done (до #27): compose — postgres, kafka, keycloak, jaeger, caddy, ollama (профиль llm-local); healthcheck Java-сервисов исправлен в #35 |
| PLT-03 | NFR-13 | CI `documents-ci.yml` уже есть — проверить на первом коде; включить деплой (`DOCS_DEPLOY_ENABLED`) | B1 | done #40 — CI проверен на коде, smoke на PR с инфраструктурой; CD переписан (compose build → GHCR → SSH, smoke, откат), включается DOCS_DEPLOY_ENABLED, когда будет сервер |
| PLT-04 | — | Синк контрактов: API.md, `/internal/**`, `contracts/events/*.schema.json`, коды и стадии | B1 + все | done #40 — scripts/check-contracts.py в CI: коды ошибок ↔ API.md ↔ фронт, стадии; openapi-diff уже был |
| PLT-05 | FR-60/61 | Keycloak realm `documents`: клиенты, роли, TOTP, brute-force, демо-пользователи из `.env` | B1 | done (до #27): realm documents — клиенты, роли, TOTP у senior, brute-force, демо-пользователи |
| PLT-06 | — | Service accounts `svc-intake/docproc/review`, `aud` на каждый сервис | B1 | done #40 — aud-мапперы svc-docproc→intake,ocr; svc-review→intake,docproc; проверка aud в intake/docproc/review/gateway; svc-intake не нужен (intake никого не вызывает) |
| PLT-07 | NFR-17 | Outbox relay + IdempotentConsumer + конверт события в `libs/common` (перенос из «Проходной») | B1 | done (до #27): OutboxRelay, IdempotentConsumer, EventEnvelope в libs/common |
| PLT-08 | NFR-01 | README «с чистого клона», `smoke.sh`, проверка на чистой машине | B1 | done #30 + smoke в CI (#35) |
| PLT-09 | FR-28 | `eval/`: `run.py` через публичный API, разметка, отчёт; синтетика `eval/synthetic/` + запуск в CI | B2 | done #28 |

## Совместные
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| X-01 | FR-14/50 | Две схемы входа на фронте: OIDC оператора и ключ заявки соискателя; проверка с gateway | B1 + F1 + F2 | done #27 |
| X-02 | FR-01/12 | Контракт загрузки и стадий: коды отказов, SSE-события, тексты и подсказки | B1 + B2 + F1 | done #40 — сверка кодов/стадий автоматом (check-contracts.py), недостающие коды дописаны в API.md |
| X-03 | FR-23/52 | Формат результата: поля, `bbox` в координатах страницы, уверенность, `checks[]` — для просмотрщика | B2 + B3 + F2 | done #19, #27 |
| X-04 | FR-42 | Замер моделей на открытом наборе → таблица в LLM.md, выбор локальной модели | B2 + B3 | todo |
| X-05 | — | «Злоумышленник» по чек-листу I (каждые 2 часа, по очереди) | все | done #40 — scripts/attack.sh (чек-лист I + T1–T10) в smoke CI |
| X-06 | NFR-12 | Слайды, матмодель (уверенность, очередь), репетиции | F2 + B2 + B1 | done #40 — MATH.md (уверенность, порог, контрольные суммы, M/M/c), 10 слайдов pptx; репетиции — за командой |
| X-07 | FR-20/42 | Разбор реальной моделью на стенде: найти причину `FAILED` у всех документов в бенче X-04 (5 повторов зависимости ≈ 60 с — вероятно OCR или intake, не модель); в attack.sh требовать стадию дальше `EXTRACTING_TEXT` (WAITING_MODEL без модели). Причины: JDK HttpClient слал h2c-upgrade → uvicorn OCR отвечал 400 (фикс: HTTP/1.1 во всех клиентах); промпты `*.md` не попадали в образ (.dockerignore); на CPU — `LLM_NUM_THREAD` и потолок `num_predict` 2048. Стенд, qwen3:4b CPU: syn-001/002/003 → 26/27 полей верно, MRZ-проверки зелёные, ~2–6 мин на документ | B1 + B2 | done #85 |
| X-08 | NFR-10 | Удаление заявки / отзыв согласия: `DELETE /applicant/applications/{id}` стирает файлы, анкету, контакты; docproc — текст, результаты, поля; review — кэш, правки, ФИО, комментарии; история обезличивается (redacted), запись `APPLICATION_DELETED` остаётся; кнопка в кабинете | B1 + B2 + F1 | done #68 |
