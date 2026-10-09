# TASKS — review

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| RV-01 | NFR-15 | `new-service.sh review 8083 B3`, Liquibase: queue_items, assignments, application_history(+trigger) | B3 | done `feat/RV-01-review` |
| RV-02 | FR-50/63 | Роли OPERATOR/SENIOR_OPERATOR/AUDITOR, `RoleMatrixTest` | B3 | done `feat/RV-01-review` |
| RV-03 | FR-51 | Проекции из событий → очередь, фильтры, курсоры, «взять следующую» | B3 | done `feat/RV-01-review` |
| RV-04 | FR-52 | Карточка: результат docproc + правки + проверки + уверенность | B3 | done `feat/RV-01-review` |
| RV-05 | FR-53/B-08 | Правка поля/типа, подтверждение, история append-only | B3 | done `feat/RV-01-review` |
| RV-06 | FR-55 | Запрос «дослать/переснять» → `ResubmitRequested`; «разобрать заново» → docproc | B3 | done `feat/RV-01-review` |
| RV-07 | FR-57 | Решение по заявке, SSE очереди | B3 | done `feat/RV-01-review` |
| RV-08 | B-03 | Сверка между документами (`findings`): ФИО с транслитерацией, даты, номера | B3 | done `feat/RV-01-review` |
| RV-09 | B-04 | Истекающие документы + выгрузка XLSX | B3 | done `feat/RV-01-review` |
| RV-10 | B-05 | Карточка соискателя для кадров (DOCX/XLSX/PDF) | B3 | done `feat/RV-01-review`; PDF — #45 (PDFBox + Liberation Sans) |
| RV-11 | B-06 | Метрики очереди из `stage_events`/`decisions`, «как посчитано» | B3 | done `feat/RV-01-review` |
| RV-12 | B-07 | Маскирование ПДн для AUDITOR, аудит просмотров файлов | B3 | done `feat/RV-01-review` |
| RV-13 | FR-56 | Пустая БД → честные нули; тест на пустой схеме | B3 | done `feat/RV-01-review` |
| RV-14 | FR-03 | Тот же номер документа (вид + поле, нормализованный; серия+номер) в другой заявке → замечание `NUMBER_ELSEWHERE` в карточке, номер не раскрываем | B3 | done #64 |
