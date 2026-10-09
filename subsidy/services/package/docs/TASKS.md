# TASKS — package

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| PK-01 | NFR-15 | `new-service.sh package 8081 B1`, Liquibase: organizations, packages, files, file_versions, outbox | B1 | done |
| PK-02 | FR-01 | Организация по ИНН (контрольная сумма), пакет на программу, роли + IDOR-тест | B1 | done |
| PK-03 | FR-03/06 | `FileInspector` + проверки — TDD на битых файлах | B1 | done |
| PK-04 | FR-02 | Приём: AES-GCM + запись + outbox, `202`, `FileReceived` без имени | B1 | done |
| PK-05 | FR-03 | `ZipExpander`: лимиты, пути, вложенность; тест zip-бомбы | B1 | done |
| PK-06 | FR-04 | Дубликаты sha256 | B1 | todo |
| PK-07 | FR-66 | `/internal/package/files/{id}` (svc-docproc), `/files/{id}/content` (роль + аудит) | B1 | todo |
| PK-08 | FR-62 | Консьюмеры docproc/rules → стадии и статус пакета, SSE | B1 | todo |
| PK-09 | FR-05/65 | Версии файла: замена → `FileReplaced` → переразбор | B1 | todo |
| PK-10 | FR-63 | Превью страниц (ocr `/v1/render`), кэш | B1 | todo |
| PK-11 | FR-61 | Очередь пакетов: фильтры, курсоры, счётчики из протокола | B1 | todo |
| PK-12 | — | Решение по пакету (SENIOR), `PackageDecided` | B1 | todo |
