# TASKS — межмодульные задачи и вехи (владелец B1)

Задачи модулей — в их `TASKS.md`:
[gateway](../services/gateway/docs/TASKS.md) · [intake](../services/intake/docs/TASKS.md) · [docproc](../services/docproc/docs/TASKS.md) · [ocr](../services/ocr/docs/TASKS.md) · [review](../services/review/docs/TASKS.md) · [libs/llm](../services/libs/llm/docs/TASKS.md) · [frontend](../frontend/docs/TASKS.md).
Здесь — платформа (B1) и задачи для двоих и больше. Формат и статусы — `WORKFLOW.md §2`. Кто что делает сейчас — открытые draft PR (`gh pr list --draft`).

## Вехи (PLAN.md)
| Веха | Когда | Условие готовности | Статус |
|---|---|---|---|
| M0 Каркас | Т+0:45 | `init-backend.sh services` + сервисы собираются, compose поднимает pg/kafka/keycloak/ollama, фронт-каркас с токенами, CI зелёный | todo |
| M1 Контракты | Т+1:00 | API.md, `/internal/**`, `contracts/events/`, схемы 8 типов в DOC-TYPES согласованы | todo |
| M2 Интеграция №1, тег `docs-demo-1` | Т+3:00 | анкета → загрузка → стадии → поля по модели → оператор видит карточку; рестарт docproc посреди разбора — без потерь | todo |
| M3 Интеграция №2, тег `docs-demo-2` | Т+5:00 | 8 видов на открытом наборе (отчёт eval), сбой модели → WAITING_MODEL, правки и история, закрытость по чек-листу I; код-фриз P0/P1 | todo |
| M4 Финал, тег `docs-demo-final` | Т+6:30 | docs, DECISIONS, EVAL-отчёт, слайды, 2 репетиции | todo |

## Платформа (B1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| PLT-00 | — | Получить текст кейса: сверить С1–С22, разд. 5.3/5.5 с REQUIREMENTS, DOC-TYPES, DEMO; поправить гипотезы | B1 + все | todo |
| PLT-01 | NFR-15 | `init-backend.sh services` (кейс documents), `libs/common` — перенести из `proxodnaya/services/libs/common` (sed `proxodnaya` → `documents`), `libs/llm` — каркас библиотеки | B1 | todo |
| PLT-02 | NFR-01/02 | `infra/docker-compose.yml`: postgres (БД+роль на сервис, volume), kafka + `topics.sh`, keycloak (realm из файла), jaeger, caddy, ollama + ollama-pull (профиль `llm-local`) | B1 | todo |
| PLT-03 | NFR-13 | CI `documents-ci.yml` уже есть — проверить на первом коде; включить деплой (`DOCS_DEPLOY_ENABLED`) | B1 | todo |
| PLT-04 | — | Синк контрактов: API.md, `/internal/**`, `contracts/events/*.schema.json`, коды и стадии | B1 + все | todo |
| PLT-05 | FR-60/61 | Keycloak realm `documents`: клиенты, роли, TOTP, brute-force, демо-пользователи из `.env` | B1 | todo |
| PLT-06 | — | Service accounts `svc-intake/docproc/review`, `aud` на каждый сервис | B1 | todo |
| PLT-07 | NFR-17 | Outbox relay + IdempotentConsumer + конверт события в `libs/common` (перенос из «Проходной») | B1 | todo |
| PLT-08 | NFR-01 | README «с чистого клона», `smoke.sh`, проверка на чистой машине | B1 | todo |
| PLT-09 | FR-28 | `eval/`: `run.py` через публичный API, разметка, отчёт; синтетика `eval/synthetic/` + запуск в CI | B2 | todo |

## Совместные
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| X-01 | FR-14/50 | Две схемы входа на фронте: OIDC оператора и ключ заявки соискателя; проверка с gateway | B1 + F1 + F2 | todo |
| X-02 | FR-01/12 | Контракт загрузки и стадий: коды отказов, SSE-события, тексты и подсказки | B1 + B2 + F1 | todo |
| X-03 | FR-23/52 | Формат результата: поля, `bbox` в координатах страницы, уверенность, `checks[]` — для просмотрщика | B2 + B3 + F2 | todo |
| X-04 | FR-42 | Замер моделей на открытом наборе → таблица в LLM.md, выбор локальной модели | B2 + B3 | todo |
| X-05 | — | «Злоумышленник» по чек-листу I (каждые 2 часа, по очереди) | все | todo |
| X-06 | NFR-12 | Слайды, матмодель (уверенность, очередь), репетиции | F2 + B2 + B1 | todo |
