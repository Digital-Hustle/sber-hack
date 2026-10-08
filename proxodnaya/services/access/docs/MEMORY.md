# MEMORY — access

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- Gradle-монорепа инициализирована (init-backend.sh), access-сервис создан (new-service.sh, порт 8082).
- Готово: ACC-01 (миграции), ACC-02 (QrParser+QrVerifier), ACC-05 (KioskController), ACC-07 (DecisionEngine), ACC-08 (JournalService hash-chain).
- libs/common: ErrorCode, DomainException, ProblemDetailFactory, CurrentUser, EventEnvelope, Topics, OutboxEntry.
- Тесты: 36 unit (DecisionEngine 14, QrParser 9, QrVerifier 6, ArchitectureTest 7) — зелёные. Интеграционные требуют Docker.
- Ветка: feat/ACC-01-access-skeleton. Далее: ACC-03 (гашение integration test), ACC-04 (проекции), ACC-06 (параллельные клиенты+CB).

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `src/main/java/.../shared/` | Direction, Decision, AttemptSource, ReasonCode, ReasonView, Challenge, ChallengeAction |
| `src/main/java/.../features/qr/` | QrPayload, QrParser, QrVerifier |
| `src/main/java/.../features/pass/` | PassContext, FaceResult, DecisionResult, DecisionEngine, PassService, JournalService |
| `src/main/java/.../features/pass/gateway/` | FaceClient, PeopleClient, ShiftClient, AccessProfile, ActiveShift, FaceVerifyResult |
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
| — | — | — |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-08 · miroslavrov · docs: документы сервиса (ADR-034)
- 2026-10-08 · KIRI4444 · feat: ACC-01/02/05/07/08 — Gradle-каркас, миграции, QrParser+QrVerifier, KioskController, DecisionEngine, JournalService (hash-chain), libs/common, 36 unit-тестов
