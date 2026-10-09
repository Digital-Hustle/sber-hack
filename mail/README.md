# Общий почтовый узел сервера

Один postfix на все кейсы (как `edge/` для 80/443). Кейсы шлют письма на `172.17.0.1:2525` без логина и пароля,
узел отдаёт их в `smtp.beget.com:465` (TLS) от `no-reply@digital-hustle.ru`. Пароль ящика лежит только в `mail/.env`
на сервере — в кейсах секрета нет.

```bash
cd mail && cp .env.example .env   # вписать RELAYHOST_PASSWORD
docker compose up -d
docker compose logs -f postfix     # status=sent — письмо ушло
```

| Кейс | Что выставить в `infra/.env` |
|---|---|
| documents | `SMTP_HOST=172.17.0.1`, `SMTP_PORT=2525`, `STAFF_LOG_CODES=false` |
| proxodnaya | то же + `SMTP_STARTTLS=false` |

Безопасность: порт опубликован только на docker-мосту, `mynetworks` — только локальные сети docker,
отправитель принудительно `no-reply@digital-hustle.ru` (открытым релеем узел стать не может).
