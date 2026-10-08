# Backend — специфика «Проходной»

Общие правила (раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, jOOQ + Liquibase, outbox, Kafka, тесты, Python face) — **скилл `backend-spring`** (`.agents/skills/backend-spring/SKILL.md`). Здесь только то, что относится к кейсу.

## Сервисы (ADR-020…030)
| Сервис | Порт | Владелец | Суть |
|---|---|---|---|
| gateway | 8080 | B1 | Spring Cloud Gateway, JWT, `/<svc>/api/v1/**` → `/api/v1/**`, `/internal/**` наружу не пускает |
| people | 8081 | B1 | рабочие, допуски, устройства и их ключи, device-токены, офлайн-снапшот |
| access | 8082 | B2 | DecisionEngine, гашение QR (`qr_uses`), журнал проходов (append-only), anti-passback |
| face | 8090 | B2 | Python/FastAPI: `/internal/v1/enroll`, `/internal/v1/match` (YuNet + SFace + MiniFASNet) |
| shift | 8083 | B3 | смены, интервалы, табель XLSX, SSE, оповещения (бывший notify) |
| assistant | 8084 | B3 | `LlmGateway`, read-only инструменты, валидатор чисел |

Новый сервис: `.agents/skills/backend-spring/scripts/new-service.sh <svc> <port> <Owner> [--no-db|--gateway]`, затем маршрут в gateway, контейнер в `infra/docker-compose.yml` (без `ports:`), БД и роль в `infra/postgres/init.sql`, client в realm Keycloak, строка в CI-матрице, владелец в CODEOWNERS.

## Инварианты кейса
- **Решение о проходе — только access** (Д2). Fail-closed: face/people/shift недоступны или таймаут 1,5 с → `DENY SYSTEM_ERROR`, никаких «пропустим, раз не ответил».
- **Гашение QR атомарно**: `INSERT INTO qr_uses(device_id, window) ... ON CONFLICT DO NOTHING` (0 строк → `QR_REUSED`), запись в `pass_attempts`, anti-passback и outbox — один `@Transactional` в access.
- Проверка QR: формат `v1.<deviceId>.<window>.<sig>`, окно 30 с с допуском ±90 с (`QR_EXPIRED`), подпись ECDSA P-256 ключом устройства (`QR_INVALID`), отозванное устройство → `DEVICE_REVOKED`.
- **Пройти за другого нельзя** (Д10): QR → workerId → face `match` 1:1 с эталонами этого рабочего + liveness. Не 1:N.
- Журнал `pass_attempts` append-only: триггер запрещает UPDATE/DELETE (см. `backend-spring/references/data-messaging.md`).
- Эмбеддинги и фото — в Postgres face, AES-GCM (`BIOMETRY_KEY`). MinIO/Qdrant запрещены (лицензия/не нужны).
- Офлайн-режим киоска = ручной проход охранника под флагом (ADR-029), с пометкой в журнале и досинхронизацией `offline-attempts`.
- События — только через outbox; топики `<владелец>.<агрегат>.v<N>` (`access.pass.v1`, `people.worker.v1`, `shift.shift.v1`).

## Обязательные тесты
- access: `DecisionEngineTest` (каждый код отказа), `QrVerifierTest` (окно, подпись, формат), `QrConsumeIntegrationTest` (20 параллельных гашений → 1 успех), `FailClosedIntegrationTest` (face/people/shift недоступны → DENY), `PassAttemptsAppendOnlyIntegrationTest`, `OutboxIntegrationTest`.
- face: pytest на своих/чужих фото и спуфинге (фото команды с согласия — не в git; иначе синтетика).
- shift: `IntervalCalculatorTest` (ночная смена, нет выхода, смена зоны), идемпотентность консюмера.
- assistant: `NumberValidatorTest`, тест prompt injection.
- Во всех: `EndpointSecurityMatrixTest`, `ArchitectureTest`.

## Команды
```bash
cd services
./gradlew :access:check                    # unit + IT (Testcontainers) + checkstyle + ArchUnit
./gradlew :access:update :access:generateJooq   # Liquibase → jOOQ (нужен Postgres)
./gradlew exportOpenApi                    # contracts/openapi/<svc>.json
node ../../.agents/skills/backend-spring/scripts/be-check.mjs .
cd face && uv run ruff check . && uv run mypy . && uv run pytest
```
Миграция: `scripts/new-migration.sh <svc> <slug>` → `services/<svc>/src/main/resources/db/changelog/changes/<ts>-<slug>.sql` (face → `services/face/db/changelog/`). Смерженные changeset-ы не правим.
