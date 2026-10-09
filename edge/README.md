# Общий сервер: edge-прокси для трёх кейсов

Порты 80/443 на сервере один на всех, поэтому их держит общий Caddy (`edge/`). Он получает сертификаты и по имени хоста
отправляет запрос в Caddy нужного кейса, а тот слушает только http на `127.0.0.1:<порт>`.

| Кейс | Хост | Порт кейса | Что выставить в `infra/.env` кейса |
|---|---|---|---|
| documents | `documents.<домен>` | 8180 | `PUBLIC_HOST=documents.<домен>`, `CADDY_SCHEME=http://`, `HTTP_BIND=127.0.0.1:8180`, `HTTPS_BIND=127.0.0.1:8543` |
| subsidy | `subsidy.<домен>` | 8280 | то же с портами 8280/8643 — нужна такая же правка Caddyfile/compose кейса (см. ниже) |
| proxodnaya | `proxodnaya.<домен>` | 8380 | то же с портами 8380/8743 |

Keycloak у всех кейсов живёт под `/auth` того же хоста, поэтому отдельные `auth.*` записи не нужны.

## DNS
У регистратора/в DNS-зоне домена — по A-записи на IP сервера (или одна wildcard):

```
documents   A   217.12.40.215
subsidy     A   217.12.40.215
proxodnaya  A   217.12.40.215
; либо одной записью:
*           A   217.12.40.215
```
TTL 300. Если у домена есть AAAA-записи на другой адрес — удалить (Let's Encrypt проверит IPv6 первым). CAA, если задан,
должен разрешать `letsencrypt.org`. Пока DNS не готов — подойдёт `documents.217.12.40.215.sslip.io` (резолвится сам).

## Правка кейса для работы за edge (две строки)
- Caddyfile: адрес сайта `{$CADDY_SCHEME}{$PUBLIC_HOST}` и в глобальном блоке `servers { trusted_proxies static private_ranges }`.
- compose: `ports: ["${HTTP_BIND:-80}:80", "${HTTPS_BIND:-443}:443"]`, в `environment` caddy — `CADDY_SCHEME: ${CADDY_SCHEME:-}`.
Без переменных кейс работает как раньше (сам на 80/443).

## Запуск
```
cd edge && cp .env.example .env   # вписать хосты
docker compose up -d && docker compose logs -f edge   # видно получение сертификатов
```
