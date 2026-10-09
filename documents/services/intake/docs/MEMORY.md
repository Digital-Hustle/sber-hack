# MEMORY — intake

Память модуля между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- Реализовано: заявки, ключ (HMAC-хэш), анкета → doc_requirements, приём файлов (FileInspector, AES-GCM, sha256/dHash дубликаты, лимиты), outbox DocumentReceived/FilesChanged/DocumentRemoved, консьюмер стадий docproc и решений review, SSE по билету, internal API для docproc/review, контент файлов с аудитом.
- Тесты: FileInspectorTest (все коды FR-01), RequirementsEngineTest, IntakeFlowIntegrationTest (сценарий, IDOR, роли) на PostgreSQL; контракт contracts/openapi/intake.json.
- Не сделано: IN-12 (несколько документов в одном файле).

## 2. Карта модуля
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| _появится в Т0_ | по `docs/ARCHITECTURE.md §Структура папок` |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | файлы в PostgreSQL, AES-256-GCM | Д3, MinIO нельзя по лицензии | ADR-007 |
| до Т0 | тип не зависит от пункта загрузки | СН за подгонку | ADR-006 |
| до Т0 | набор — данные `doc_requirements` | меняется без кода | ADR-010 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| — | — | — |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-09 · miroslavrov · docs: документы модуля
- 2026-10-09 · miroslavrov · feat: intake реализован (ветка feat/IN-01-intake)
- 2026-10-09 · agent · IN-14 (B-07): `applications.assignee` (миграция application-assignee) из события `ApplicationAssigned`; `FileContentService.ensureStaffAccess` — скан оператору только по назначению
