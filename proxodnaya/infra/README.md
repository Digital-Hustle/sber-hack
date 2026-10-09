# infra — стенд «Проходной»

```bash
cd proxodnaya/infra
../scripts/gen-env.sh                 # .env со случайными секретами, PUBLIC_HOST=localhost
# образы: из GHCR (CD) или локально — jar-ы Gradle и сборка
(cd ../services && ./gradlew bootJar) # нужен Postgres для jOOQ (см. services/AGENTS)
docker compose build
docker compose up -d --wait           # + --profile frontend / assistant / llm-local по мере готовности
../scripts/smoke.sh https://localhost
```

| Адрес | Что |
|---|---|
| `https://<host>/auth` | Keycloak, realm `proxodnaya` (импорт из `keycloak/realm-proxodnaya.json`, плейсхолдеры `${ENV}` из `.env`) |
| `https://<host>/<svc>/api/v1/**` | API через gateway |
| `https://<host>/actuator/health` | health gateway |
| остальное | SPA (`frontend`) |

- Наружу публикуется только Caddy (80/443). Postgres, Kafka, Keycloak-admin, сервисы, face — только во внутренней сети.
- Postgres: при первом старте `postgres/init.sh` создаёт БД и роль на сервис + `keycloak` (ADR-017). Пароли — из `.env`.
- Kafka: KRaft, один брокер, топики создаются при первой публикации.
- Keycloak: роли `ADMIN`, `MANAGER`, `SECURITY_OFFICER`, `GUARD`, `SVC_*`; клиенты `gate-admin`, `gate-kiosk` (PKCE), `svc-*` (client credentials, `aud` по вызовам), `gate-provisioner`; демо-`admin` с паролем `KC_DEMO_ADMIN_PASSWORD` и обязательным TOTP. Подробнее — `../docs/SECURITY.md §2`, ADR-039.
- Realm импортируется только в пустую БД Keycloak; после правки JSON — `docker compose down -v` (или удалить realm в админке).
