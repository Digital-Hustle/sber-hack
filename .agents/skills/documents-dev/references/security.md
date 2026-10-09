# Security (подробно: docs/SECURITY.md)

- Realm `infra/keycloak/realm-documents.json`, секреты — плейсхолдеры `${KC_...}`. Роли: OPERATOR, SENIOR_OPERATOR (TOTP), AUDITOR. Соискатели не в Keycloak.
- Ключ заявки: 256 бит, в БД `sha256` с перцем `ACCESS_KEY_PEPPER`; область — одна заявка; чужой id → 404; код возврата — 5 попыток, затем 429.
- Файлы: AES-256-GCM (`FILES_ENCRYPTION_KEY`), формат `iv(12)|ciphertext|tag`; `/internal/intake/files/{id}` — только `svc-docproc` (`aud=intake`).
- Межсервисно: service accounts `KC_SVC_<NAME>_SECRET`, целевой сервис проверяет `aud` и роль.
- Логи: только id и коды — без текстов документов, полей и ключей.

## Чек-лист PR с security-изменениями
- [ ] Новый эндпоинт в матрице «эндпоинт × роль» (+ IDOR для соискателя)
- [ ] Нет `ports:` у внутренних сервисов
- [ ] Нет логирования ПДн, ключей, текстов
- [ ] Ошибки не раскрывают внутренности
- [ ] Обновлена модель угроз в `docs/SECURITY.md`, если новая поверхность атаки
