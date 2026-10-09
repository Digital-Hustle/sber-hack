# MEMORY — review

Память модуля между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- RV-01..13 реализованы (ветка feat/RV-01-review): проекция из событий, очередь, карточка, правки, решение, история, сверка, отчёты, метрики, маскирование, SSE.
- Одна фича `features/workplace` (CQRS: command/query/application/gateway/rules); PDF не делаем — только DOCX/XLSX (POI, Apache-2.0).
- Назначения хранятся в `applications.assignee` (без отдельной таблицы assignments); findings считаются на лету при открытии карточки.

## 2. Карта модуля
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `features/workplace/command` | ProjectionService (события → проекция), ActionService (действия + история + outbox), TicketService (SSE) |
| `features/workplace/query` | QueryService + ReadRepository: очередь (курсор), карточка, история (маски для AUDITOR), отчёт, метрики |
| `features/workplace/application` | WorkplaceService (вызовы intake/docproc вне транзакций, SSE), ExportService (DOCX/XLSX), StreamHub |
| `features/workplace/rules` | NameMatcher (ГОСТ 52535.1 + скелет), CrossChecker, Masking, Fields |
| `messaging/consumer` | EventsConsumer: intake.application.v1, intake.document.v1, docproc.document.v1 |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | история — append-only таблица с триггером | B-08, доказуемо | ADR-001 |
| до Т0 | SSE для очереди | проще WebSocket, проходит gateway | ADR-009 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-09 | SSE-подписки в памяти процесса: при нескольких репликах review событие видят только подключённые к той же реплике | на стенде одна реплика; дальше — Postgres LISTEN/NOTIFY |
| 2026-10-09 | Номер в карточке маскирован и у оператора; полный — POST /reveal (запись REVEALED в историю) | B-07 |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-09 · miroslavrov · docs: документы модуля
- 2026-10-09 · агент · feat: review RV-01..13 (ветка feat/RV-01-review)
