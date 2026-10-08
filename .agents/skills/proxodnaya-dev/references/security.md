# Security (подробно: docs/SECURITY.md)

## Keycloak
- Realm-файл: `infra/keycloak/realm-proxodnaya.json`. Секреты — только плейсхолдеры `${KC_...}`, значения в `.env`.
- Меняешь роли или клиенты → правь realm-файл и пересоздай контейнер: `docker compose up -d --force-recreate keycloak` (импорт с `--import-realm` идёт только если realm ещё не существует; для повторного импорта удали realm или volume dev-окружения).
- Роли: ADMIN, MANAGER, SECURITY_OFFICER, GUARD. WORKER не в Keycloak — у рабочих ключ устройства.

## Backend
- `SecurityConfig` каждого сервиса (общая часть — в `libs/common`): `oauth2ResourceServer` с `JwtIssuerAuthenticationManagerResolver` для двух издателей: `https://auth.<host>/realms/proxodnaya` (люди) и издателя device-токенов people (`proxodnaya-device`).
- `KeycloakRoleConverter`: `realm_access.roles` → `ROLE_*`.
- Device-токены рабочих: challenge (`POST /auth/device/challenge`) → подпись ключом устройства → `POST /auth/device/token` → ES256 JWT на 10 мин, `sub=worker:<id>`, `did=<deviceId>`.
- Подпись запросов киоска: заголовки `X-Kiosk-Id`, `X-Ts`, `X-Nonce`, `X-Signature` (ECDSA над `method|path|ts|nonce|sha256(body)`); nonce хранится 60 с в таблице `kiosk_nonces` (уникальный индекс).
- Шифрование биометрии (в face, Python `cryptography` AESGCM) с ключом `BIOMETRY_KEY` (base64, 32 байта) из env; формат `iv(12)|ciphertext|tag`.

- Межсервисно: `ServiceTokenClient` берёт токен service account (`KC_SVC_<NAME>_SECRET`), кэширует до истечения; целевой сервис проверяет `aud` и роль `SVC_<caller>`.
- Device-токены и публичные ключи устройств — в people; подпись QR и подпись киоска проверяет access.
- QR офлайн: `v1.<deviceId>.<window>.<sig>`, ECDSA P-256 неизвлекаемым ключом телефона; повтор ловит `qr_uses` (ADR-028).

## Чек-лист для любого PR с security-изменениями
- [ ] Новый эндпоинт есть в `EndpointSecurityMatrixTest` своего сервиса
- [ ] Новый сервис/эндпоинт не проброшен наружу мимо gateway (нет `ports:` в compose)
- [ ] Нет логирования токенов, ПДн, биометрии
- [ ] Ошибки не раскрывают внутренности (stack trace, SQL)
- [ ] Доступ к чужим данным проверяется в сервисе, а не только во фронте
- [ ] Обновлена таблица угроз в `docs/SECURITY.md`, если появилась новая поверхность атаки
