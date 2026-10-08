# TASKS — people

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| PPL-01 | NFR-15 | `new-service.sh people 8081 B1`, БД и роль, миграции базовых таблиц | B1 | todo |
| PPL-02 | FR-52 | Рабочие: CRUD, курсоры, `options?q=`, блок, outbox `people.worker.v1` | B1 | todo |
| PPL-03 | FR-13 | Инвайт: код + QR, срок, одноразовость | B1 | todo |
| PPL-04 | FR-02/63 | Активация `{inviteCode, deviceId, publicKeyJwk}`, `DeviceBound` в outbox | B1 | todo |
| PPL-05 | FR-63 | Device challenge → token ES256 10 мин, JWKS | B1 | todo |
| PPL-06 | FR-22 | Зоны, проходные, вместимость | B1 | todo |
| PPL-07 | FR-64 | Код сопряжения киоска → ключ киоска, `/auth/kiosk/pair` | B1 | todo |
| PPL-08 | FR-07 | Допуски: инструктаж/медосмотр со сроком, допуски в зоны; `/internal/.../access-profile` | B1 | todo |
| PPL-09 | FR-60 | Provisioning персонала в Keycloak (`POST /staff`) | B1 | todo |
| PPL-10 | NFR-10 | Удаление рабочего: каскад в face, `WorkerDeleted` | B1 | todo |
| PPL-11 | FR-65 | `EndpointSecurityMatrixTest`, IDOR-тесты | B1 | todo |
