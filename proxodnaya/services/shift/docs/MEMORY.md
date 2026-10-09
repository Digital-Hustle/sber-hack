# MEMORY — shift

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- Есть ядро (#37): смены и шаблоны, массовое назначение, `active-shift` для access, консьюмер `access.pass.v1` → интервалы, автозакрытие, `/intervals`, `/analytics/summary|hours`, `/worker/me|shifts`.
- SSE (SH-08): тикеты и буфер событий — в памяти (один экземпляр), события — после коммита из `IntervalService` (`interval/api/PassRecorded|PresenceChanged`).
- Табель (SH-09): `/reports/timesheet.xlsx`, ФИО/подрядчик — из проекции `workers` (`people.worker.v1`); рабочий без карточки — строкой с id.
- Опоздания/переработки (SH-10): `/analytics/late-overtime`, пороги `LATE_GRACE` (5 мин) и `OVERTIME_THRESHOLD` (15 мин); выход не отмечен — переработка не считается.
- Отказы и нагрузка (SH-12): `/analytics/refusals`, `/analytics/load-by-hour` по `pass_events`.
- Оповещения (SH-11): `POST /worker/heartbeat`, правила раз в минуту (`AlertJob`): LEFT_SITE/NO_SIGNAL для людей в смене на объекте, ZONE_CROWDED; геозона — `SITE_LAT/SITE_LON/SITE_RADIUS_M`, лимит — `CROWD_LIMIT` (один на все зоны).
- Дальше: напоминание о выходе (SH-13).
- Пояс площадки — `SITE_ZONE` (Europe/Moscow), допуск раннего входа — `SHIFT_EARLY_ENTRY` (60 мин).

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `features/shift` | смены, шаблоны, `ShiftPlanner` (недельный график → даты, ночь, DST), `api/ShiftLookup` (active-shift) |
| `features/interval` | `IntervalPlan` (правила интервалов), `IntervalService` (проход → интервалы + outbox, автозакрытие), `AutoCloseJob`, `api/PresenceLookup` |
| `features/worker` | статус рабочего (`/worker/me`), проекция карточек `workers` (`WorkerDirectoryService`) |
| `features/report` | табель: `TimesheetService` (интервалы → дни площадки, коды Т/Н), `TimesheetXlsxWriter` (POI) |
| `features/analytics` | SQL сводки и часов, `LateOvertimeCalculator` (смена против интервалов), `PassStatsQueryService` (отказы по причинам, нагрузка день × час по `pass_events`), `method` в ответе |
| `features/analytics` | SQL сводки и часов, `LateOvertimeCalculator` (смена против интервалов), `method` в ответе |
| `features/alert` | `HeartbeatService` (последний heartbeat + геозона), `AlertRules` (B-01), `AlertService` (B-05, одно активное на условие, outbox `shift.alert.v1`, SSE `alert.created`), `AlertJob` |
| `features/stream` | `TicketService` (одноразовые тикеты), `SseHub` (подписчики, буфер, Last-Event-ID, пульс), `StreamEventsListener` (события после коммита → SSE) |
| `messaging/consumer/PassEventsConsumer` | `access.pass.v1` → `pass_events` + интервалы, идемпотентно |
| `messaging/consumer/PeopleEventsConsumer` | `people.worker.v1` → `workers` (ФИО, должность, подрядчик, статус), старое событие не перетирает новое |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | notify влит в shift | см. ADR | ADR-030 |
| до Т0 | Kafka + outbox, идемпотентные потребители | см. ADR | ADR-024 |
| до Т0 | SSE вместо WebSocket | см. ADR | ADR-008 |
| 2026-10-09 | пересечение смен — под `pg_advisory_xact_lock` рабочего, а не exclusion constraint | `btree_gist` требует расширения, которого может не быть у роли сервиса | #37 |
| 2026-10-09 | табель: Т — был на объекте хоть раз за день, Н — плановая смена прошла без прохода; день с невыясненным выходом — жёлтым и в «Неполных днях» | честность цифр: часы без выхода не выдумываем | #59 |
| 2026-10-09 | опоздание — по первому входу в окне смены (`SHIFT_EARLY_ENTRY` до начала … конец), переработка — по последнему выходу; при неотмеченном выходе переработка 0 и смена в incomplete | не выдумывать часы без выхода | #60 |
| 2026-10-09 | системные отказы — только `FACE_LOW_QUALITY` и `SYSTEM_ERROR` (B-07), остальные правомерные; нагрузка — день × час в `SITE_ZONE` | как в REQUIREMENTS B-07/B-08 | #62 |
| 2026-10-09 | NO_SIGNAL — только если телефон уже присылал heartbeat; LEFT_SITE — по heartbeat после входа; точность в пользу человека | без приложения на телефоне не спамить оповещениями | #65 |
| 2026-10-09 | геозона и лимит скопления — из env, а не из people | в people нет координат площадки и событий зон; вынести в `people.zone.v1` позже | #65 |
| 2026-10-09 | повторный вход без выхода закрывает прежний интервал `REENTRY` + `UNCONFIRMED_EXIT`, а не «как будто вышел» | честность цифр (rules.md) | #37 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-09 | лимит скопления один на все зоны (`CROWD_LIMIT`), `capacity` зоны из people не учитывается | нужен `people.zone.v1` |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-08 · miroslavrov · docs: документы сервиса (ADR-034)
- 2026-10-09 · miroslavrov · feat(shift): SH-01..07 — каркас, смены/шаблоны/массовое назначение, active-shift для access, интервалы из access.pass.v1, автозакрытие, summary/hours, worker/me
- 2026-10-09 · miroslavrov (агент) · chore: JWKS и token-uri из внутренней сети (`OIDC_JWKS_URI`, `OIDC_TOKEN_URI`), iss — публичный (ADR-039, #53)
- 2026-10-09 · miroslavrov (агент) · feat: SH-08 SSE — тикет, `/stream/situation`, `/stream/worker`, Last-Event-ID, пульс (#57)
- 2026-10-09 · miroslavrov (агент) · feat: SH-09 табель XLSX (`/reports/timesheet.xlsx`), проекция `people.worker.v1` → `workers` (#59)
- 2026-10-09 · miroslavrov (агент) · feat: SH-10 опоздания и переработки (`/analytics/late-overtime`, `LATE_GRACE`, `OVERTIME_THRESHOLD`) (#60)
- 2026-10-09 · miroslavrov (агент) · feat: SH-12 отказы по причинам и нагрузка по часам (`/analytics/refusals`, `/analytics/load-by-hour`) (#62)
- 2026-10-09 · miroslavrov (агент) · feat: SH-11 heartbeat и оповещения LEFT_SITE/NO_SIGNAL/ZONE_CROWDED (`/worker/heartbeat`, `/alerts`, SSE `alert.created`, `shift.alert.v1`) (#65)
