# MEMORY — people

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- Есть каркас (PPL-01) и рабочие (PPL-02): CRUD, курсор по UUIDv7, поиск, блок, outbox `people.worker.v1`. Инвайты и активация телефона (PPL-03/04) → `DeviceBound`/`DeviceRevoked` для access. Объекты/зоны/проходные и допуски (PPL-06/08), `/internal/people/**` под ролью `SVC_ACCESS`. Эталон лица (PPL-12) и каскад удаления в face (PPL-10): `FaceGateway` с токеном `svc-people`, вызов face вне транзакции. Дальше: сервисные токены (PLT-06) — без них access получит 401; PPL-05 device-токен; PPL-07 сопряжение киоска.
- people — второй издатель JWT (ES256, ADR-040): device-токен телефона по подписи challenge (PPL-05), JWKS `/internal/people/jwks`; ключ — env `DEVICE_TOKEN_SIGNING_KEY`.

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `api/` | `WorkerController(+Impl)`, Rq/Rs, `WorkerApiMapper` (MapStruct) |
| `features/worker/` | `command` (WorkerCommandService + outbox), `query` (поиск, курсор), `model` |
| `messaging/outbox/` | Outbox, OutboxRepository, OutboxRelay (как в access) |
| `exception/ApiExceptionHandler` | problem+json, `errors[]` для полей, коды → HTTP-статус |
| `features/device/` | инвайты (`InviteService`, `InviteCodes` HMAC), активация/отвязка (`DeviceService`, `DeviceKeys` — проверка JWK) |
| `features/worker/api/` | `WorkerDirectory`/`WorkerCard` — рабочий для других фич |
| `features/site/` | объекты, зоны, проходные; `api/ZoneDirectory` для проверки зон рабочего |
| `features/token/` | `TokenIssuer` / `JwtTokenIssuer` — выпуск ES256, открытая часть ключа выводится из закрытого |
| `features/device/command/service/DeviceAuthService` | challenge → device-токен (PPL-05), `ChallengeRepository` |
| `api/controller/InternalController` | `/internal/people/**` для access (`SVC_ACCESS`) |
| `shared/` | `WorkerStatus`, `Cursor`, `Limits` |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | Keycloak для персонала, ключ устройства для рабочих | см. ADR | ADR-011 |
| до Т0 | QR подписывает ключ устройства, сервер хранит только публичный | см. ADR | ADR-028 |
| до Т0 | события через outbox в Kafka | см. ADR | ADR-024 |
| 2026-10-08 | Подрядчик — строка в `workers`, без справочника `contractors` | хватит для MVP | PPL-02 |
| 2026-10-08 | Код инвайта хранится как HMAC-SHA256 с `INVITE_PEPPER` | 6 символов перебираются за минуты при утечке БД | PPL-03 |
| 2026-10-08 | Активация публичная (`permitAll`) | у рабочего нет токена, подтверждает одноразовый инвайт; rate-limit — gateway GW-05 | PPL-04 |
| 2026-10-08 | Проходная ведёт в одну зону (`zone_id`), вместо `zone_from/zone_to` | вход в зону = IN, выход = OUT; хватает для MVP | PPL-06 |
| 2026-10-09 | Открытый ключ выводим `d·G`, а не берём из PKCS#8 | Java не кладёт точку в PKCS#8, openssl — кладёт; так работают оба | PPL-05 |
| 2026-10-09 | Challenge выдаётся и для неизвестного устройства | ответ не раскрывает, привязан ли id | PPL-05 |
| 2026-10-08 | Outbox скопирован из access, не в `libs/common` | jOOQ-классы у сервисов свои | PLT-07 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-08 | Каскад удаления в face не сделан | PPL-10 |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-08 · miroslavrov · docs: документы сервиса (ADR-034)
- 2026-10-08 · miroslavrov · feat: PPL-01/02 — каркас people, рабочие, outbox `people.worker.v1`
- 2026-10-08 · miroslavrov · feat: PPL-03/04 — инвайты, активация телефона, список/отвязка устройств
- 2026-10-08 · miroslavrov · feat: PPL-06/08 — объекты, зоны, проходные, допуски, access-profile
- 2026-10-09 · miroslavrov · PPL-10/12: эталон лица через face, каскад удаления, `faceEnrolledAt`
- 2026-10-09 · miroslavrov (агент) · chore: JWKS и token-uri из внутренней сети (`OIDC_JWKS_URI`, `OIDC_TOKEN_URI`), iss — публичный (ADR-039, #53)
- 2026-10-09 · miroslavrov (агент) · feat: ACC-17 `zoneCapacity` в `/internal/people/checkpoints/{id}` для access (#90)
- 2026-10-09 · miroslavrov (агент) · feat: PPL-05 device-токен ES256, JWKS, два издателя в сервисах и gateway (ADR-040, #PR)
