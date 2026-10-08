# Backend (микросервисы)

## Структура
```
services/
  pom.xml                         # parent: Java 21, Spring Boot 3.x, Spring Cloud BOM, модули ниже
  mvnw
  libs/common/                    # ProblemDetail, GateException, SecurityConfig (multi-issuer), KeycloakRoleConverter,
                                  # ServiceTokenClient (client credentials), Outbox + OutboxPublisher, IdempotentConsumer,
                                  # EventEnvelope, AesGcm, RequestId/trace фильтры. БЕЗ бизнес-логики.
  gateway/                        # Spring Cloud Gateway: маршруты /api/<svc>/** → http://<svc>:8080, JWT, rate-limit, CORS
  people/ access/ face/ shift/ assistant/ notify/
    src/main/java/ru/team/gate/<svc>/
      api/        # DTO (record), клиенты к другим сервисам
      domain/     # сущности, сервисы, правила
      infra/      # JPA, внешние клиенты (OpenCV — только face, LLM — только assistant)
      web/        # контроллеры: /api/** (внешние, через gateway) и /internal/** (только SVC_*)
      messaging/  # слушатели и публикация через outbox
    src/main/resources/application.yml, db/migration/
    Dockerfile                    # общий шаблон: eclipse-temurin:21-jre, non-root
contracts/openapi/<svc>.json      # snapshot OpenAPI
contracts/events/<type>.schema.json
```

## Новый сервис
Скопируй ближайший существующий сервис как шаблон, переименуй пакет, добавь модуль в `services/pom.xml`, маршрут в gateway, контейнер в `infra/docker-compose.yml` (без `ports:`), БД и роль в `infra/postgres/init.sql`, client в realm Keycloak, job в CI-матрицу, владельца в CODEOWNERS.

## Правила
- Чужая БД — запрещено. Нужны данные — `/internal/**` API владельца или события.
- Синхронный вызов другого сервиса: `RestClient` из `libs/common` с service-токеном, таймаут 1,5 с, Resilience4j CB. В пути прохода — ошибка = `DENY SYSTEM_ERROR`, в остальных местах — честная ошибка 503 с кодом.
- Публикация события — только `outbox.add(event)` в той же транзакции, что и изменение данных. Прямой `rabbitTemplate.send` в бизнес-коде запрещён.
- Потребитель: `@RabbitListener` + `IdempotentConsumer.once(eventId, ...)`.
- access-service: гашение токена (`UPDATE ... WHERE used_at IS NULL AND expires_at > now()`), запись в `pass_attempts`, состояние anti-passback и outbox — один `@Transactional`.
- Каждый контроллер с `@PreAuthorize`; `/internal/**` — `hasRole('SVC_ACCESS')` и т. п. `EndpointSecurityMatrixTest` в каждом сервисе.
- Время — внедрённый `Clock`. Ошибки — `GateException(code, status, messageRu)` → ProblemDetail.
- `spring.jpa.open-in-view=false`, `ddl-auto=validate`, `spring.flyway.out-of-order=true`.
- Трейсинг: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`, endpoint `http://jaeger:4318`.

## Flyway
- `scripts/new-migration.sh <svc> <slug>` → `services/<svc>/src/main/resources/db/migration/V<ts>__<slug>.sql`.
- Смерженные миграции не правим. В access: `REVOKE UPDATE, DELETE ON pass_attempts FROM access_app;`

## Тесты
- access: `DecisionEngineTest` (каждый код отказа), `TokenConsumeIT` (20 параллельных гашений → 1 успех), `FailClosedTest` (face/people/shift недоступны → DENY), `OutboxIT`.
- face: сверка своих/чужих фото из `src/test/resources` (фото команды с согласия, не в git публично — или синтетика).
- shift: `IntervalCalculatorTest` (ночная смена, нет выхода, смена зоны), идемпотентность слушателя.
- assistant: `NumberValidatorTest`, тест prompt injection.
- Запуск: `cd services && ./mvnw -q -pl <svc> -am verify`; всё — `./mvnw -q verify`.

## Контракты
`./mvnw -q -pl <svc> -am verify -Pcontract` пишет `contracts/openapi/<svc>.json`; CI сверяет. Схемы событий правятся руками в `contracts/events/` и проверяются тестом в libs/common.
