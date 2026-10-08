# MEMORY — people

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- Есть каркас (PPL-01) и рабочие (PPL-02): CRUD, курсор по UUIDv7, поиск, блок, outbox `people.worker.v1`. Дальше PPL-03/04 (инвайт + активация → `DeviceBound` для access), PPL-06/08 (зоны, допуски, access-profile).
- Открытые вопросы: Где хранить ключ подписи device-токенов (env vs Keycloak как издатель) — сейчас env, см. SECURITY.md.

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `api/` | `WorkerController(+Impl)`, Rq/Rs, `WorkerApiMapper` (MapStruct) |
| `features/worker/` | `command` (WorkerCommandService + outbox), `query` (поиск, курсор), `model` |
| `messaging/outbox/` | Outbox, OutboxRepository, OutboxRelay (как в access) |
| `exception/ApiExceptionHandler` | problem+json, `errors[]` для полей, коды → HTTP-статус |
| `shared/` | `WorkerStatus`, `Cursor`, `Limits` |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | Keycloak для персонала, ключ устройства для рабочих | см. ADR | ADR-011 |
| до Т0 | QR подписывает ключ устройства, сервер хранит только публичный | см. ADR | ADR-028 |
| до Т0 | события через outbox в Kafka | см. ADR | ADR-024 |
| 2026-10-08 | Подрядчик — строка в `workers`, без справочника `contractors` | хватит для MVP | PPL-02 |
| 2026-10-08 | Outbox скопирован из access, не в `libs/common` | jOOQ-классы у сервисов свои | PLT-07 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-08 | Каскад удаления в face не сделан | PPL-10 |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-08 · miroslavrov · docs: документы сервиса (ADR-034)
- 2026-10-08 · miroslavrov · feat: PPL-01/02 — каркас people, рабочие, outbox `people.worker.v1`
