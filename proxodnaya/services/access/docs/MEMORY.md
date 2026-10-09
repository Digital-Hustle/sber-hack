# MEMORY — access

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- Поток прохода end-to-end: скан QR → челлендж → кадры → решение; гашение QR + журнал + присутствие + outbox в одной транзакции (`PassRecorder`).
- Есть: ручной проход охранника, журнал `/attempts` с keyset-курсором, зона проходной из people, симулятор (ACC-14).
- Симулятор: `PassRecorder.simulated` — журнал + `access.pass.v1` с `source=SIMULATOR`, присутствие не трогает; флаг `SIMULATOR_ENABLED`.
- Вместимость (ACC-17): `worker_state.zone_id` + `PresenceService.zoneFull` под `pg_advisory_xact_lock` зоны в `PassRecorder.finish`; `capacity` — `zoneCapacity` из `/internal/people/checkpoints/{id}` при кадрах входа.
- Терминалы (ACC-12): `features/kiosk` — регистрация ключа → код → сопряжение в кабинете; `KioskSignatureFilter` проверяет подпись `/api/v1/kiosk/**` (nonce в `kiosk_nonces`), проходная — из привязки.
- Коды терминала (ACC-19): PBKDF2 в PostgreSQL, кабинет видит только метаданные; сопряжённый киоск получает хэши в `/kiosk/me`. Код не заменяет GUARD JWT.
- Нет: circuit breaker, офлайн (ACC-15), `/internal/access/stats` (ACC-16), `/kiosk/identify`.

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `src/main/java/.../shared/` | Direction, Decision, AttemptSource, ReasonCode, ReasonView, Challenge, ChallengeAction |
| `src/main/java/.../features/qr/` | api: QrCheck, QrUses, DeviceKeyProjection; command: QrParser, EcdsaVerifier, PublicKeyCodec, QrCheckService, QrUseService, DeviceKeyService |
| `src/main/java/.../features/pass/` | application: PassService, DecisionEngine, PassRecorder (транзакция); command: Challenge/Journal/Presence; query: PassQueryService |
| `src/main/java/.../features/pass/application/Simulat*` | симулятор: SimulationGenerator (чистая функция от зерна), SimulatorService; API — SimulatorController |
| `src/main/java/.../features/pass/gateway/` | FaceGateway, PeopleGateway, ShiftGateway (RestClient) |
| `src/main/java/.../messaging/` | outbox (Outbox, OutboxRelay), consumer (PeopleEventsConsumer, IdempotentConsumer) |
| `src/main/java/.../features/kiosk/` | терминалы: KioskService, KioskAuthenticator (подпись), KioskRepository, CheckpointGateway |
| `src/main/java/.../config/KioskSignatureFilter` | подпись запросов терминала → `KioskAuthentication` (ROLE_KIOSK) |
| `src/main/java/.../api/` | KioskController, KioskSelfController, KiosksController (+impl), DTO request/response, PathConstants |
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
- 2026-10-09 · KIRI4444, miroslavrov · feat(access): ACC-09/11/13 — anti-passback под блокировкой присутствия, журнал `GET /attempts` и `/worker/attempts` (keyset-курсор, `{items, page}`), ручной проход `POST /kiosk/manual` (MANUAL_GUARD, присутствие, охранник и причина в журнале)
- 2026-10-09 · miroslavrov (агент) · chore: JWKS и token-uri из внутренней сети (`OIDC_JWKS_URI`, `OIDC_TOKEN_URI`), iss — публичный (ADR-039, #53)
- 2026-10-09 · miroslavrov (агент) · feat: ACC-17 вместимость зоны → `ZONE_FULL` (`worker_state.zone_id`, блокировка зоны, `zoneCapacity` от people) (#90)
- 2026-10-10 · KIRI4444, miroslavrov (агент) · feat(access): ACC-18 настройки прохода `GET|PUT /api/v1/settings` (jsonb-строка `access_settings`, кэш 5 с, дефолты из env); порог лица, допуск QR, пауза, блокировка, присутствие и `requireShift` применяются сразу (`PassTuning`); `shiftGraceMin`, режим терминала и офлайн — пока только хранятся (#92)
- 2026-10-09 · miroslavrov (агент) · feat(access): ACC-12 терминалы — регистрация ключа, код сопряжения, `/kiosk/me`, `/kiosks` (список, pair, patch, unpair), подпись запросов `X-Kiosk-*` с окном времени и одноразовым nonce; проходная скана — из привязки терминала (#109)

- 2026-10-10 · miroslavrov (агент) · feat: ACC-19 коды терминала: API, PBKDF2 и матрица ролей, хэши только сопряжённому; frontend берёт код онлайн из `/kiosk/me`, нет заводского доступа после сопряжения.
