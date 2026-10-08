# Стек

Принципы выбора: всё поднимается одной командой `docker compose up`, всё под разрешёнными лицензиями, минимум движущихся частей, максимум того, что агенты хорошо знают.

## Backend
| Что | Выбор | Лицензия | Зачем |
|---|---|---|---|
| Язык | Java 21 (Temurin) | GPLv2+CPE (рантайм, не вирусный для приложения) | требование |
| Архитектура | микросервисы: gateway + 6 сервисов, Maven multi-module (`services/`) | — | ADR-015 |
| Фреймворк | Spring Boot 3.3+: Web, Security, Data JPA, Validation, Actuator | Apache-2.0 | стандарт, агенты знают |
| Gateway | Spring Cloud Gateway | Apache-2.0 | единая точка входа |
| Межсервисные вызовы | Spring `RestClient` + Resilience4j (таймаут, CB) | Apache-2.0 | fail-closed |
| События | RabbitMQ 3.13 + Spring AMQP, transactional outbox | MPL-2.0 / Apache-2.0 | ADR-016 |
| Трейсинг | Micrometer Tracing + OpenTelemetry → Jaeger | Apache-2.0 | ADR-019 |
| БД | PostgreSQL 16 | PostgreSQL | Д4 |
| Миграции | Flyway | Apache-2.0 | воспроизводимая схема |
| Identity / SSO | **Keycloak 26** (OIDC, SSO, TOTP, brute-force защита, брокеринг внешних IdP) | Apache-2.0 | персонал: ADMIN / MANAGER / SECURITY_OFFICER / GUARD |
| Аутентификация в API | Spring Security OAuth2 Resource Server, multi-issuer (Keycloak + device-токены рабочих) | Apache-2.0 | рабочие входят ключом устройства, без пароля |
| Шифрование | AES-256-GCM (JCA) для биометрии, ECDSA P-256 для ключей устройств | — | T11, T3, T5 из SECURITY.md |
| Лицо | OpenCV 4.9+ Java (`org.openpnp:opencv`): **YuNet** (детекция, MIT) + **SFace** (эмбеддинг 128-d, Apache-2.0) | Apache-2.0 / MIT | работает в JVM, без отдельного сервиса |
| Живость (опционально) | ONNX Runtime Java + MiniFASNet (Silent-Face-Anti-Spoofing) | MIT / Apache-2.0 | пассивная проверка «фото с экрана» |
| LLM-клиент | собственный порт + адаптеры на Spring `RestClient` | — | изоляция слоя модели (Д6) |
| Устойчивость | Resilience4j (timeout, retry, circuit breaker) | Apache-2.0 | честное поведение при сбое модели |
| Realtime | Server-Sent Events (`SseEmitter`) | — | проще WebSocket, работает через прокси |
| Excel | Apache POI | Apache-2.0 | табель для бухгалтерии (бонус) |
| API-доки | springdoc-openapi | Apache-2.0 | генерация TS-клиента |
| Наблюдаемость | Actuator + Micrometer Prometheus + logstash-logback-encoder | Apache-2.0 | инженерная культура |
| Тесты | JUnit 5, AssertJ, Testcontainers, ArchUnit | EPL/Apache/MIT | движок, изоляция модулей |

## Frontend
| Что | Выбор | Лицензия |
|---|---|---|
| Сборка | Vite + React 18 + TypeScript strict, pnpm workspaces | MIT |
| UI | Mantine 7 (+ @mantine/charts на Recharts) | MIT |
| Данные | TanStack Query, `openapi-typescript` + `openapi-fetch` | MIT |
| Роутинг | React Router | MIT |
| OIDC | `oidc-client-ts` + `react-oidc-context` | Apache-2.0 / MIT |
| QR показ | `qrcode.react` | ISC |
| QR скан | `@zxing/browser` | Apache-2.0/MIT |
| Камера | `getUserMedia` (нативный API) | — |
| PWA | `vite-plugin-pwa` | MIT |
| Realtime | нативный `EventSource` | — |

## LLM
| Режим | Провайдер | Модель |
|---|---|---|
| Облако (по умолчанию на защите, если дадут ключ) | **GigaChat API** (на хакатоне Сбера это плюс) | GigaChat-2 / Pro |
| Облако-альтернатива | OpenAI-совместимый endpoint | любой |
| **Локально (обязательно, Д6)** | **Ollama** в docker compose (профиль `llm-local`) | `qwen3:4b` (Apache-2.0), при наличии GPU `qwen3:8b` |

Переключение: `LLM_PROVIDER=gigachat|openai|ollama` в `.env`. Код не меняется.

## Инфраструктура
- `docker compose`: `caddy`, `frontend`, `gateway`, `people`, `access`, `face`, `shift`, `assistant`, `notify`, `keycloak`, `postgres`, `rabbitmq`, `jaeger`, `ollama` (профиль `llm-local`), `prometheus` (опционально). Сервер и CI/CD — см. DEPLOY.md.
- **HTTPS обязателен**: браузер даёт доступ к камере и геолокации только в secure context. Варианты по убыванию надёжности: (1) VPS + домен + Let's Encrypt через Caddy; (2) Caddy `tls internal` в локальной сети + корневой сертификат на телефоны; (3) туннель (cloudflared). Решить до хакатона, см. QUESTIONS.md.
- Секреты: `.env` + `.env.example`; `gitleaks` в pre-commit и CI.
- CI: GitHub Actions — `mvn verify`, `pnpm build`, `gitleaks`.

## Сознательно НЕ берём
- Kafka (тяжёлая для ноутбука), Redis (AGPL/SSPL), Kubernetes и service mesh: на один день лишнее.
- MinIO (AGPL), Grafana (AGPL), iText (AGPL): лицензии.
- Python-сервис для лица: второй язык и ещё один контейнер. Это план Б, если OpenCV Java не взлетит за первый час (spike).
- Хранить фото на диске: фото и эмбеддинги лежат в Postgres (`bytea`/`real[]`).
