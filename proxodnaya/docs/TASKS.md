# TASKS — межмодульные задачи и вехи (владелец B1)

Задачи модулей — в их `TASKS.md`:
[gateway](../services/gateway/docs/TASKS.md) · [people](../services/people/docs/TASKS.md) · [access](../services/access/docs/TASKS.md) · [face](../services/face/docs/TASKS.md) · [shift](../services/shift/docs/TASKS.md) · [assistant](../services/assistant/docs/TASKS.md) · [frontend](../frontend/docs/TASKS.md).
Здесь — платформа (B1) и задачи, где нужны двое и больше. Формат и статусы — `WORKFLOW.md §2`. Кто что делает прямо сейчас — открытые draft PR (`gh pr list --draft`).

## Вехи (PLAN.md)
| Веха | Когда | Условие готовности | Статус |
|---|---|---|---|
| M0 Каркас | Т+0:45 | `init-backend.sh` + 5 сервисов собираются, compose поднимает pg/kafka/keycloak, фронт-каркас с токенами, CI зелёный | todo |
| M1 Контракты | Т+1:00 | API.md, `/internal/**`, схемы `contracts/events/` согласованы всеми (PLT-04) | todo |
| M2 Интеграция №1, тег `demo-1` | Т+3:00 | человек, заведённый через UI, проходит по QR + лицу; DENY при чужом лице | todo |
| M3 Интеграция №2, тег `demo-2` | Т+5:00 | челлендж, anti-passback, отчёты, помощник; атака по чек-листу H; код-фриз P0/P1 | todo |
| M4 Финал, тег `demo-final` | Т+6:30 | docs, DECISIONS, слайды, 2 репетиции | todo |

## Платформа (B1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| PLT-01 | NFR-15 | `init-backend.sh` → `services/` (build-logic, version catalog, checkstyle), `libs/common` | B1 | todo |
| PLT-02 | NFR-01/02 | `infra/docker-compose.yml`: postgres (БД+роль на сервис), kafka + `topics.sh`, keycloak (realm из файла), jaeger, caddy | B1 | review #53 |
| PLT-03 | NFR-13 | CI: матрица сервисов, фронт, gitleaks, guard, trivy; деплой на сервер | B1 | todo |
| PLT-04 | — | Синк контрактов: API.md, `/internal/**`, `contracts/events/*.schema.json` | B1 + все | todo |
| PLT-05 | FR-60/62 | Keycloak realm: клиенты admin/kiosk/api/provisioner, роли, TOTP, brute-force | B1 | review #53 |
| PLT-06 | — | Service accounts и `aud` на каждый сервис, `ServiceClients` в `libs/common` | B1 | doing: service accounts и `aud` есть (#53), `ServiceClients` в libs/common — нет |
| PLT-07 | NFR-17 | Outbox relay + IdempotentConsumer + конверт события в `libs/common` | B3 (по просьбе B1) | todo |
| PLT-08 | NFR-01 | README «с чистого клона», `smoke.sh`, проверка на чистой машине | B1 | todo |

## Совместные (пары из ROLES.md)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| X-01 | FR-61/63 | OIDC-клиенты для admin/kiosk, device-auth рабочего — проверка с фронтом | B1 + F1 + F2 | todo |
| X-02 | FR-01/12 | Контракт киоска: `/kiosk/attempts`, `/frames`, формат QR, коды отказов, `checks[]` | B2 + F1 | todo |
| X-03 | FR-13 | Формат кадров (JPEG base64 640×480, 3–5 шт.), эндпоинт заведения эталона | B2 + F2 | todo |
| X-04 | FR-30/42 | Формат аналитики и ответа помощника (text + sources + table) | B3 + F2 | todo |
| X-05 | — | Атака по чек-листу H (каждые 2 часа, по очереди) | все | todo |
| X-06 | NFR-12 | Слайды, матмодель FAR/FRR, репетиции защиты | F2 + B2 + B1 | todo |
