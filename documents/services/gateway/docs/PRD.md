# PRD — gateway

Продукт целиком — `../../../docs/PRD.md`, требования с ID — `../../../docs/REQUIREMENTS.md`. Здесь — только этот модуль.

## Обзор
Единая точка входа для двух зон фронтенда (`/apply` соискателя и `/operator`): один origin, проверка токена на входе, маршрутизация на intake, docproc, review. Сервисы, ocr, Ollama и Kafka — во внутренней сети (ADR-001).

## Проблемы
- Три Java-сервиса и ocr — фронту нужен один адрес, один CORS и один TLS.
- Два вида доступа: оператор (Keycloak) и соискатель без пароля (ключ заявки, ADR-008) — оба нужно принимать.
- Загрузки большие (до `UPLOAD_MAX_MB` × файлов) — лимит тела и rate-limit должны резать флуд до сервисов.
- `/internal/**` (выдача расшифрованных файлов!) не должен быть доступен снаружи даже по ошибке.

## Цели
1. Только gateway открыт наружу; `/internal/**` → 404 на входе.
2. JWT оператора проверяется на входе, ключ заявки — пропускается только на `/intake/api/v1/applicant/**`; сервисы всё равно проверяют доступ сами.
3. `traceparent`/`X-Request-Id` на каждый запрос — путь документа виден одним трейсом в Jaeger.
4. SSE стадий и очереди проходит без буферизации.

## Пользователи
- Фронтенд (обе зоны) — через `https://<host>/<svc>/api/v1/**`.
- Судьи — косвенно: health, Swagger.

## Ключевые функции
- `RewritePath /<svc>/api/v1/** → /api/v1/**` для intake, docproc, review.
- OAuth2 Resource Server (WebFlux), issuer — Keycloak realm `documents`.
- Публичные без JWT: `POST /intake/api/v1/applicant/applications` (создание заявки), `/intake/api/v1/applicant/**` с `Authorization: Applicant <accessKey>` (проверяет intake), health.
- Лимит тела: `UPLOAD_MAX_MB` на `…/documents`, остальное 1 МБ.
- Rate-limit на создание заявки и загрузку (in-memory счётчики — не данные).
- SSE (`/stream/**`) без таймаута ответа.

## MVP
| Обязательно (P0, к интеграции №1–2) | Потом (P1/P2, если есть время) |
|---|---|
| маршруты, JWT, блок `/internal/**`, CORS, лимит тела, trace-заголовки | rate-limit, заголовки безопасности (если не Caddy), агрегированный Swagger |

Задачи и статусы — `TASKS.md`.
