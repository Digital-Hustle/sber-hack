# TASKS — shift

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| SH-01 | NFR-15 | `new-service.sh shift 8083 B3`, миграции shifts/templates/intervals/outbox | B3 | review #37 |
| SH-02 | FR-20 | Смены и шаблоны, массовое назначение бригаде | B3 | review #37 |
| SH-03 | FR-07/21 | `/internal/shift/workers/{id}/active-shift` (окно, ночные смены) для access | B3 | review #37 |
| SH-04 | FR-22/23 | Консьюмер `access.pass.v1` → интервалы, идемпотентность, `IntervalCalculatorTest` | B3 | review #37 |
| SH-05 | FR-23 | Автозакрытие `AUTO_SHIFT_END` + `UNCONFIRMED_EXIT` | B3 | review #37 (автозакрытие AUTO_SHIFT_END; UNCONFIRMED_EXIT как флаг) |
| SH-06 | FR-24/33 | Отчёт по человеку и «отработано часов» за период | B3 | review #37 (`/analytics/hours`; отчёт по человеку = `hours?worker=`) |
| SH-07 | FR-30/32 | `/analytics/summary` «сейчас на объекте» + `method`, `incomplete` | B3 | review #37 (`summary`: inside по зонам, incomplete, проходы за день) |
| SH-08 | B-02 | SSE-хаб + ticket, `/stream/situation`, `/stream/worker`, Last-Event-ID | B3 | review #57 |
| SH-09 | B-06 | Табель XLSX (Apache POI) | B3 | review #59 (проекция `people.worker.v1` → `workers` для ФИО/подрядчика) |
| SH-10 | B-03 | Опоздания и переработки | B3 | review #PR (`/analytics/late-overtime`) |
| SH-11 | B-01/B-05 | Heartbeat, оповещения: покинул геозону/нет связи, скопление в зоне | B3 | todo |
| SH-12 | B-07/B-08 | Отказы по причинам и нагрузка по часам | B3 | todo |
| SH-13 | B-04 | Напоминание о невыходе через 30 мин после смены (SSE worker) | B3 | todo |
