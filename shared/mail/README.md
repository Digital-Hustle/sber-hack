# Digital Hustle — общая отправка почты (MAIL-01)

Один SMTP-клиент и один ящик `no-reply@digital-hustle.ru` для «Проходной», «Документов» и «Субсидий». Это клиент существующего почтового провайдера, не собственный сервер входящей почты. FastAPI/Uvicorn — MIT/BSD-3-Clause; Python SMTP — стандартная библиотека (PSF).

## Настройка на сервере

1. Скопировать `.env.example` в `/etc/digital-hustle/mail.env` с правами `600`.
2. Заполнить SMTP_HOST, SMTP_PORT, SMTP_TLS (`starttls` или `ssl`), SMTP_USER, SMTP_PASSWORD. Пароль приложения предпочтительнее пароля ящика.
3. Создать SHARED_MAIL_TOKEN (`openssl rand -hex 32`), передать тот же токен сервисам-клиентам через их приватный env.
4. `shared/mail/deploy.sh` из корня репозитория.
5. API доступен **только на сервере**: `127.0.0.1:8390`. Не публиковать через edge/Caddy. Контейнеры клиентов подключаются к сети `digital-hustle-mail`, например через `proxodnaya.override.yml`.

Без SMTP_HOST сервис жив, но отправка возвращает 503 «Почта ещё не подключена» — успешная доставка не имитируется. SMTP_HOST нельзя угадывать по адресу отправителя. Для домена нужны SPF/DKIM/DMARC у почтового провайдера.

## Подключение «Проходной»

В приватном `proxodnaya/infra/.env`: `SHARED_MAIL_ENABLED=true`, `SHARED_MAIL_URL=http://digital-hustle-mail:8080`, тот же `SHARED_MAIL_TOKEN`. Запуск с дополнительным compose-файлом: `docker compose -f proxodnaya/infra/docker-compose.yml -f shared/mail/proxodnaya.override.yml --env-file proxodnaya/infra/.env up -d people`. У всех остальных сервисов остаётся прежняя сеть. `STAFF_LOG_CODES` не подменяет ответ общего почтового клиента и не выводит коды при использовании его.

## Внутренний контракт

`POST /internal/mail`, `Authorization: Bearer SHARED_MAIL_TOKEN`:

```json
{"service":"proxodnaya","kind":"login","email":"user@example.com","code":"123456","minutes":10}
```

`service`: proxodnaya / documents / subsidy. `kind`: login / invitation; для приглашения используются `name`, `role`, `invited_by`. Ответ 204 означает, что SMTP-провайдер принял письмо, не что оно попало во входящие. Ошибки: 401 без токена, 422 неверные поля, 503 SMTP не настроен/не принял письмо.

HTML и plain-text содержат название кейса, код, срок, предупреждение о безопасности и подпись автоматического сервиса Digital Hustle. Код не включён в тему и не логируется. Клиентские API самостоятельно генерируют, хэшируют, ограничивают попытки и гасят код — почтовый сервис не выполняет вход и не хранит коды.

## Вход без виджета Keycloak

Целевой UI всех кейсов: собственная форма почты → шесть ячеек кода → настоящая серверная сессия с HttpOnly refresh-cookie. Образец — `proxodnaya/frontend/src/pages/login` и `shared/ui/CodeInput.tsx`. Keycloak остаётся для сервисных аккаунтов; SMTP сам по себе **не превращает TOTP в код из письма**. «Документы» и «Субсидии» требуют отдельной реализации серверной email-аутентификации и миграции доверенных издателей JWT; текущие iframe/OIDC нельзя удалять до готовности этого пути.

## Статус работ

| Задача | Статус |
| --- | --- |
| MAIL-01: общий SMTP-клиент, письма, интеграция people | PR #113 |
| MAIL-02: серверный email-вход «Документов» и формы из «Проходной» вместо iframe | Не реализовано; отдельный PR с тестами ролей и сессий |
| MAIL-03: серверный email-вход «Субсидий» и формы из «Проходной» вместо OIDC-экрана | Не реализовано; отдельный PR с тестами ролей и сессий |
| SMTP на общем сервере | Ожидаются host, port, TLS и пароль приложения |
