# MEMORY — access

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- Поток прохода работает end-to-end (PR #3): скан QR → челлендж → кадры → решение; гашение QR + журнал + присутствие + outbox в одной транзакции.
- QR — 5 частей с `workerId`, подпись WebCrypto P1363. Ключи устройств — проекция `people.device.v1` (DeviceBound/DeviceRevoked), статус — `people.worker.v1`.
- face/people/shift — RestClient, параллельно, таймаут `DEPS_TIMEOUT`, fail-closed → `SYSTEM_ERROR` с именем зависимости в `details`.
- Нет: circuit breaker, service-токен на `/internal/**`, подпись киоска (ACC-12, пока `checkpointId` в теле), зона проходной, алерт TEMP_LOCKED.
- Тесты: 53 unit (QrParser, крипто, QrCheck, DecisionEngine, PassService, роли, ArchUnit) + интеграционные (Testcontainers: гонка гашения, сквозной киоск, append-only).

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `src/main/java/.../shared/` | Direction, Decision, AttemptSource, ReasonCode, ReasonView, Challenge, ChallengeAction |
| `src/main/java/.../features/qr/` | api: QrCheck, QrUses, DeviceKeyProjection; command: QrParser, EcdsaVerifier, PublicKeyCodec, QrCheckService, QrUseService, DeviceKeyService |
| `src/main/java/.../features/pass/` | application: PassService, DecisionEngine, PassRecorder (транзакция); command: Challenge/Journal/Presence; query: PassQueryService |
| `src/main/java/.../features/pass/gateway/` | FaceGateway, PeopleGateway, ShiftGateway (RestClient) |
| `src/main/java/.../messaging/` | outbox (Outbox, OutboxRelay), consumer (PeopleEventsConsumer, IdempotentConsumer) |
| `src/main/java/.../api/` | KioskController (+impl), DTO request/response, PathConstants |
| `src/main/java/.../config/` | SecurityConfig |
| `src/main/java/.../exception/` | DomainExceptionHandler |
| `src/main/resources/db/changelog/` | outbox, device_keys, worker_state, qr_uses, pass_attempts (+триггер) |
| `src/test/java/.../` | ArchitectureTest, AbstractIntegrationTest, QrParserTest, QrVerifierTest, DecisionEngineTest |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | офлайн-QR, подпись ключом устройства | см. ADR | ADR-028 |
| до Т0 | офлайн-режим = ручной проход охранника | см. ADR | ADR-029 |
| до Т0 | активная живость через случайный челлендж | см. ADR | ADR-004 |
| до Т0 | демо-данные только через доменный сервис с пометкой | см. ADR | ADR-007 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-08 | `window` — зарезервированное слово PostgreSQL, колонка `qr_window` | исправлено в #3 |
| 2026-10-08 | функция триггера ломала Liquibase (`;` внутри `$$`) | отдельный changeset `splitStatements:false` |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-08 · miroslavrov · docs: документы сервиса (ADR-034)
- 2026-10-08 · KIRI4444 · feat: ACC-01/02/05/07/08 — Gradle-каркас, миграции, QrParser+QrVerifier, KioskController, DecisionEngine, JournalService (hash-chain), libs/common, 36 unit-тестов
- 2026-10-08 · miroslavrov · fix(access): ревью #3 — QR по контракту (workerId, P1363), проверка подписи и гашение в пути прохода, челлендж в БД, направление сервером, REPEAT_SCAN, fail-closed, hash-chain под advisory lock, outbox + relay, проекции people, роли Keycloak, миграции чинятся, тесты
- 2026-10-09 · miroslavrov · feat(access): ACC-06 service-токен (OAuth2 client-credentials, клиент svc-access) на клиентах face/people/shift; зона пункта пропуска из people (`/internal/people/checkpoints/{id}`) → NO_ZONE_PERMIT, zone_id в попытках и PassDecided
