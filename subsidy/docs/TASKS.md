# TASKS — межмодульные задачи и вехи (владелец B1)

Задачи модулей — в их `TASKS.md`:
[gateway](../services/gateway/docs/TASKS.md) · [package](../services/package/docs/TASKS.md) · [docproc](../services/docproc/docs/TASKS.md) · [ocr](../services/ocr/docs/TASKS.md) · [rules](../services/rules/docs/TASKS.md) · [assistant](../services/assistant/docs/TASKS.md) · [libs/llm](../services/libs/llm/docs/TASKS.md) · [frontend](../frontend/docs/TASKS.md).
Здесь — платформа (B1) и задачи для двоих и больше. Формат и статусы — `WORKFLOW.md §2`. Кто что делает сейчас — открытые draft PR (`gh pr list --draft`).

## Вехи (PLAN.md)
| Веха | Когда | Условие готовности | Статус |
|---|---|---|---|
| M0 Каркас | Т+0:45 | `init-backend.sh services` + сервисы собираются, compose поднимает pg/kafka/keycloak/ollama, фронт-каркас с токенами, CI зелёный | todo |
| M1 Контракты | Т+1:00 | API.md, `/internal/**`, `contracts/events/`, схемы 8 видов, формат протокола согласованы | todo |
| M2 Интеграция №1, тег `subs-demo-1` | Т+3:00 | пакет → загрузка → стадии → тип и поля моделью → комплектность и 3+ правила → протокол на экране; рестарт docproc посреди разбора — без потерь | todo |
| M3 Интеграция №2, тег `subs-demo-2` | Т+5:00 | 8 видов на открытом наборе (отчёт eval), сегментация склеек, все P0-правила, смена типа и дозагрузка → перепроверка, закрытость по чек-листу I; код-фриз P0/P1 | todo |
| M4 Финал, тег `subs-demo-final` | Т+6:30 | docs, DECISIONS, EVAL-отчёт, слайды, 2 репетиции | todo |

## Платформа (B1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| PLT-00 | — | Получить текст кейса: сверить С1–С21, разд. 5.3/5.5 с REQUIREMENTS, DOC-TYPES, RULES-CATALOG, DEMO; поправить гипотезы | B1 + все | todo |
| PLT-01 | NFR-15 | `init-backend.sh services` (кейс subsidy), `libs/common` — перенести из «Проходной» (sed `proxodnaya` → `subsidy`), каркас `libs/llm` | B1 | done |
| PLT-02 | NFR-01/02 | `infra/docker-compose.yml`: postgres (БД+роль на сервис, volume), kafka + `topics.sh`, keycloak (realm из файла), jaeger, caddy, ollama + ollama-pull (профиль `llm-local`) | B1 | todo |
| PLT-03 | NFR-13 | CI `subsidy-ci.yml` уже есть — проверить на первом коде; включить деплой (`SUBS_DEPLOY_ENABLED`) | B1 | todo |
| PLT-04 | — | Синк контрактов: API.md, `/internal/**`, `contracts/events/*.schema.json`, стадии, коды правил | B1 + все | todo |
| PLT-05 | FR-60 | Keycloak realm `subsidy`: клиент, роли, TOTP, brute-force, демо-пользователи из `.env` | B1 | todo |
| PLT-06 | — | Service accounts `svc-package/docproc/rules/assistant`, `aud` на каждый сервис | B1 | todo |
| PLT-07 | NFR-17 | Outbox relay + IdempotentConsumer + конверт события в `libs/common` | B1 | todo |
| PLT-08 | NFR-01 | README «с чистого клона», `smoke.sh`, проверка на чистой машине | B1 | todo |
| PLT-09 | FR-28 | `eval/`: `run.py` через публичный API, разметка, отчёт; синтетика `eval/synthetic/` (генератор пакетов с корректными реквизитами) + запуск в CI | B2 | todo |

## Совместные
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| X-01 | FR-03/62 | Контракт загрузки и стадий: коды отказов, SSE-события, тексты | B1 + B2 + F1 | todo |
| X-02 | FR-33/63 | Формат результата: поля, `bbox`, уверенность, `checks[]`, сегменты — для просмотрщика | B2 + F1 | todo |
| X-03 | FR-45/67 | Формат протокола: статусы, `evidence`, ожидаемое/фактическое — для экрана и выгрузки | B3 + F2 | todo |
| X-04 | FR-52 | Замер моделей на открытом наборе → таблица в LLM.md, выбор локальной модели | B2 + B3 | todo |
| X-05 | — | «Злоумышленник» по чек-листу I (каждые 2 часа, по очереди) | все | todo |
| X-06 | NFR-12 | Слайды, матмодель (уверенность, сегментация, формулы правил), репетиции | F2 + B2 + B3 | todo |
