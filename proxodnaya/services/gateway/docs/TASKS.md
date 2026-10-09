# TASKS — gateway

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| GW-01 | NFR-15 | `new-service.sh gateway 8080 B1 --gateway`, health, Dockerfile | B1 | review #49 |
| GW-02 | NFR-15 | Маршруты `RewritePath` для people/access/shift/assistant, env-адреса | B1 | review #49 |
| GW-03 | FR-60/63 | JWT multi-issuer: Keycloak + device-токены people, тест на оба | B1 | review #49 |
| GW-04 | NFR-15 | Блок `/internal/**` (404), CORS только свои origin, список публичных путей | B1 | review #49 |
| GW-05 | FR-11 | Rate-limit `/auth/**` и `/kiosk/attempts`, 429 + Retry-After (ADR на реализацию) | B1 | review #49 |
| GW-06 | B-02 | SSE без буферизации и таймаута, лимит тела 5 МБ на киоск | B1 | review #49 |
| GW-07 | NFR-18 | `traceparent`, `X-Request-Id`, JSON-логи, метрики маршрутов | B1 | review #49 |
| GW-08 | FR-65 | Матричный тест «путь × без токена/чужой issuer/просроченный» | B1 | review #49 |
| GW-09 | FR-60/61 | Только staff refresh-cookie проходит через gateway; остальные cookies срезаются | B1 | done #114 |
