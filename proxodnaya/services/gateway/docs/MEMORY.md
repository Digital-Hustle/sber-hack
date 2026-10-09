# MEMORY — gateway

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- GW-01..08 сделаны: маршруты `/<svc>/api/v1/** → /api/v1/**`, JWT нескольких издателей (JWKS), `/internal/**` и actuator сервисов → 404, CORS только `CORS_ORIGINS`, rate-limit (окно в памяти, ADR-038), лимит тела `MAX_BODY`, SSE без таймаута, `X-Request-Id`, problem+json, 503 при недоступном upstream.
- Management (health/prometheus) — порт 8079, наружу не публикуется.
- Второй издатель — токены people (ADR-040): `gateway.issuers[1]` из env `PEOPLE_TOKEN_ISSUER`/`PEOPLE_JWKS_URI`, ES256; публичны `/people/api/v1/auth/device/{challenge,token}` (#PR).

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `config/SecurityConfig` | цепочка security: публичные пути, multi-issuer JWT, CORS |
| `config/properties/GatewayProperties` | `gateway.issuers[]`, `gateway.cors`, `gateway.rate-limit` |
| `filter/InternalPathBlockFilter` | 404 на `/internal/**`, actuator сервисов, `..`/`;` в пути |
| `filter/RequestIdFilter` | `X-Request-Id`, срез поддельных `X-User-*` |
| `filter/RateLimitFilter` | окно на клиента (последний хоп `X-Forwarded-For`), 429 + `Retry-After` |
| `exception/Problems`, `exception/UpstreamErrorHandler` | problem+json ошибок gateway, 503 `UPSTREAM_UNAVAILABLE` |
| `src/main/resources/application.yml` | маршруты, таймауты, лимит тела |
| `src/test/.../GatewayIntegrationTest` | весь gateway: upstream-эхо и JWKS на reactor-netty |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | микросервисы, наружу только gateway | см. ADR | ADR-015 |
| до Т0 | межсервисная аутентификация — service accounts Keycloak | см. ADR | ADR-018 |
| 2026-10-09 | rate-limit — фиксированное окно в памяти на адрес клиента, без Redis/Bucket4j | один экземпляр gateway, сброс при рестарте допустим | ADR-038 |
| 2026-10-09 | роли не проверяем в gateway — только подпись/срок/iss | роли и audience — забота сервиса | ARCHITECTURE |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-09 | закодированный обход (`%69nternal`, `..;`) режет файрвол Spring раньше фильтра — 400, а не 404 | до сервиса не доходит; тест ждёт 4xx |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-08 · miroslavrov · docs: документы сервиса (ADR-034)
- 2026-10-09 · miroslavrov · feat: GW-01..08 — маршруты, multi-issuer JWT, блок /internal, CORS, rate-limit, лимит тела, SSE, X-Request-Id, problem+json (#49)
