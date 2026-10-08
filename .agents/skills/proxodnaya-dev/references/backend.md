# Backend

## Структура
```
backend/
  pom.xml                       # один Maven-модуль, Java 21, Spring Boot 3.x
  src/main/java/ru/team/gate/
    GateApplication.java
    common/                     # Clock, ошибки (ProblemDetail), requestId-фильтр, crypto (AES-GCM)
    security/                   # resource server, multi-issuer, конвертер ролей Keycloak, device-токены
    people/ devices/ face/ tokens/ access/ journal/ shifts/ intervals/
    presence/ alerts/ analytics/ reports/ llm/ assistant/ audit/
      <module>/api/             # публичные интерфейсы + record DTO (только это видят другие модули)
      <module>/domain/          # сущности, сервисы, правила
      <module>/infra/           # JPA-репозитории, внешние клиенты
      <module>/web/             # контроллеры
  src/main/resources/
    application.yml
    db/migration/               # Flyway, V<yyyyMMddHHmm>__<slug>.sql
  src/test/java/...             # unit, *IT (Testcontainers), ArchitectureTest (ArchUnit)
```

## Правила
- Межмодульные вызовы только через `<module>.api`. `ArchitectureTest` это проверяет; не ослабляй его.
- Пакеты провайдеров LLM — только в `llm.infra`; OpenCV — только в `face.infra`.
- Время: внедряй `java.time.Clock`. `Instant.now()` без Clock запрещён (иначе не тестируется).
- Ошибки: бросай `GateException(code, httpStatus, messageRu)`. Глобальный `@RestControllerAdvice` превращает её в `ProblemDetail` с полем `code`. Коды — из `docs/API.md`.
- Безопасность на каждом контроллере: `@PreAuthorize("hasRole('ADMIN')")` и т. п. Новый эндпоинт без аннотации ломает `EndpointSecurityMatrixTest`.
- Транзакция решения о проходе: гашение токена + запись в журнал + интервал — в одной `@Transactional`.
- Журнал append-only: в репозитории нет методов update/delete; hash-chain считается в `journal.domain.ChainHasher`.
- Биометрия: поле `@Convert(converter = EncryptedBytesConverter.class)`.
- DTO — `record`; маппинг руками или MapStruct (Apache-2.0).
- `spring.jpa.open-in-view=false`, `ddl-auto=validate`.

## Flyway
- Создание: `scripts/new-migration.sh add_pass_tokens` → `V202610101130__add_pass_tokens.sql`.
- `spring.flyway.out-of-order=true` — PR с более ранней меткой может смержиться позже.
- Уже смерженную миграцию не правим; исправление — новой миграцией.
- Для журнала: `REVOKE UPDATE, DELETE ON pass_attempts, audit_log FROM gate_app;`

## Тесты
- Unit: `DecisionEngineTest` — по тесту на каждый код отказа + happy path.
- `TokenConsumeIT` — 20 параллельных гашений одного токена → ровно один успех.
- `IntervalCalculatorTest` — ночная смена, нет выхода, смена зоны.
- `NumberValidatorTest` — помощник.
- `EndpointSecurityMatrixTest` — каждый эндпоинт × каждая роль → ожидаемый 200/403.
- Запуск: `./mvnw -q verify`; только unit: `./mvnw -q test`.

## OpenAPI
- springdoc генерирует `/v3/api-docs`. `./mvnw -q verify -Pcontract` пишет `contracts/openapi.json`; CI сверяет snapshot.
