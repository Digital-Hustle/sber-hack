# MEMORY — rules

Память модуля между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- RUL-01..16 готовы (#43): проекция из событий → debounce-перепроверка (`recheck_requests`, SKIP LOCKED) → `ProtocolService.check` → неизменяемый протокол + `PackageChecked`.
- 25 правил в `engine/rules`, полнота — `completeness/CompletenessEngine`, параметры/чек-лист — таблица `programs` (DEMO-2026 в контексте `demo`).
- Протокол создаётся только когда все файлы в терминальной стадии; новая версия — только при изменении фактов.
- Liquibase contexts: прод `prod`, демо-стенд `prod,demo` (env `LIQUIBASE_CONTEXTS`).
- RUL-18: `projection/ProjectionRebuild` + `DocprocClient` (svc-rules → docproc `/internal`): `POST /packages/{id}/projection/rebuild` (SENIOR) и авто-пересборка в `RecheckWorker`, если у PARSED/NEEDS_REVIEW-файла нет документов в проекции. Сверка — `ProjectionService.reconcile` (upsert по resultVersion, снятие отсутствующих).
- Нет: ANOMALY_OUTLIER (RUL-17).

## 2. Карта модуля
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `money/` | Money — scale 2, HALF_UP, допуск 0.01 |
| `facts/` | FactsAssembler (проекция + правки специалиста), FieldOverrideService |
| `program/` | программы и чек-листы как данные |
| `engine/`, `engine/rules/` | RuleEngine (RULES_VERSION) и правила |
| `completeness/` | полнота, «чего не хватает», текст запроса |
| `projection/`, `messaging/` | события package/docproc → проекция |
| `recheck/` | debounce-очередь перепроверок + воркер |
| `protocol/` | снимки, запросы, экспорт DOCX/XLSX/PDF (шрифт Liberation Sans) |
| `history/`, `stats/` | история организации, частые ошибки, истекающие документы |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | правила — код, параметры — данные | Д11, смена программы без кода | ADR-006 |
| до Т0 | деньги — BigDecimal, в JSON строками | копейки | ADR-007 |
| до Т0 | протокол — неизменяемый снимок | объяснимость | ADR-008 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| — | — | — |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-09 · miroslavrov · docs: документы модуля
- 2026-10-09 · agent · feat(rules): RUL-01..16 — проекция, 25 правил, полнота, протоколы, экспорт, история, статистика (#43)
- 2026-10-09 · agent · feat(rules): RUL-18 пересборка проекции из docproc (ручная SENIOR и авто перед протоколом)
