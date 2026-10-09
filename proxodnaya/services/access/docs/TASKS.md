# TASKS — access

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| ACC-01 | NFR-15 | `new-service.sh access 8082 B2`, миграции: pass_attempts (+триггер), qr_uses, device_keys, worker_state, outbox | B2 | done #3 |
| ACC-02 | FR-02/03 | `QrParser` + `QrVerifier` (формат, подпись ECDSA, окно ±90 с) + тесты | B2 | done #3 |
| ACC-03 | FR-03 | Атомарное гашение `qr_uses` + `QrConsumeIntegrationTest` (20 параллельно → 1) | B2 | done #3 |
| ACC-04 | FR-02 | Проекции `device_keys`, `worker_state` из `people.*.v1` (идемпотентно) | B2 | done #3 |
| ACC-05 | FR-01/06 | `POST /kiosk/attempts` → челлендж; `POST …/frames` | B2 | done #3 |
| ACC-06 | FR-09/NFR-16 | Клиенты face/people/shift: параллельно, таймаут 1,5 с, CB, fail-closed + `FailClosedIntegrationTest` | B2 | review #3, #8 (параллельно + таймаут + fail-closed + service-токен client-credentials; без circuit breaker) |
| ACC-07 | FR-01/07 | `DecisionEngine` по порядку проверок, все коды из API.md + `DecisionEngineTest` | B2 | done #3 |
| ACC-08 | FR-10/67 | Журнал: hash-chain, append-only тест, `POST /security/journal/verify` | B2 | done #3 |
| ACC-09 | FR-08 | Anti-passback и вместимость зоны | B2 | review #31 (anti-passback; без `ZONE_FULL` → ACC-17) |
| ACC-10 | FR-11 | TEMP_LOCKED: 5 неудачных сверок за 10 мин + алерт | B2 | review #3 (блокировка есть, алерта нет) |
| ACC-11 | FR-10/31 | `GET /attempts` (фильтры, курсоры), `/worker/attempts`, миниатюры только ADMIN | B2 | review #31 (без миниатюр — снимок в журнал не пишется) |
| ACC-12 | FR-64 | Проверка подписи запросов киоска (`X-Kiosk-*`, nonce, окно) | B2 | todo |
| ACC-13 | FR-14 | Ручной проход GUARD с причиной, `source=MANUAL` | B2 | review #31 |
| ACC-14 | FR-34 | Симулятор проходов `source=SIMULATOR` через тот же движок, флаг | B2 | review #82 |
| ACC-15 | FR-70 | Офлайн-снимок (подпись, TTL 12 ч) и приём `offline-attempts` с конфликтами | B2 | todo |
| ACC-16 | FR-42 | `/internal/access/stats` для assistant | B2 | todo |
| ACC-17 | FR-08 | Вместимость зоны → `ZONE_FULL` (нужна `capacity` зоны из people + счётчик внутри под блокировкой) | B2 | review #PR |
