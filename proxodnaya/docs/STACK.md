# Стек

Принципы выбора: всё поднимается одной командой `docker compose up`, всё под разрешёнными лицензиями, минимум движущихся частей, максимум того, что агенты хорошо знают.

## Backend (Java) — правила в скилле `backend-spring`
| Что | Выбор | Лицензия | Зачем |
|---|---|---|---|
| Язык | Java 21 (Temurin) | GPLv2+CPE (рантайм, не вирусный для приложения) | требование, вся команда бэка на Java |
| Архитектура | gateway + 5 сервисов (people, access, face, shift, assistant) | — | ADR-015, ADR-030 |
| Сборка | **Gradle Kotlin DSL**: один wrapper, `gradle/libs.versions.toml`, convention plugins в `build-logic/` | Apache-2.0 | ADR-022, как в sueta-rnd-back / certis-api |
| Фреймворк | Spring Boot **3.5.16**: Web, Security, Validation, Actuator; Spring Cloud **2025.0.3** | Apache-2.0 | стабильная ветка, агенты её знают |
| Gateway | Spring Cloud Gateway (WebFlux), `RewritePath /<svc>/api/v1/** → /api/v1/**` | Apache-2.0 | паттерн sueta |
| Доступ к БД | **jOOQ 3.20** (codegen из схемы) | Apache-2.0 (OSS-редакция) | ADR-023, команда знает |
| Миграции | **Liquibase** formatted SQL, `includeAll`, имена `<timestamp>-slug.sql` | Apache-2.0 | ADR-023 |
| Маппинг | MapStruct 1.6 (`unmappedTargetPolicy = ERROR`) + Lombok | Apache-2.0 / MIT | |
| Межсервисные вызовы | `RestClient` + Resilience4j (таймаут, CB) | Apache-2.0 | fail-closed |
| События | **Apache Kafka 3.9 (KRaft, 1 брокер)** + Spring Kafka, transactional outbox | Apache-2.0 | ADR-024 |
| Трейсинг | Micrometer Tracing + OpenTelemetry → Jaeger | Apache-2.0 | ADR-019 |
| БД | PostgreSQL 16 (+ `pgvector` — только если понадобится поиск 1:N) | PostgreSQL | Д4 |
| Identity / SSO | **Keycloak 26** (OIDC, SSO, TOTP, brute-force защита) | Apache-2.0 | персонал: ADMIN / MANAGER / SECURITY_OFFICER / GUARD |
| Аутентификация в API | OAuth2 Resource Server в каждом сервисе, multi-issuer (Keycloak + device-токены рабочих) | Apache-2.0 | рабочие входят ключом устройства |
| Крипто | ECDSA P-256 (QR и ключи устройств), AES-256-GCM (эмбеддинги) | — | ADR-028, SECURITY.md |
| Realtime | SSE (`SseEmitter`) из shift | — | проще WebSocket |
| Excel | Apache POI | Apache-2.0 | табель |
| API-доки | springdoc-openapi 2.8 → `contracts/openapi/<svc>.json` | Apache-2.0 | генерация TS-типов |
| Качество | checkstyle 13 (конфиг sueta), ArchUnit, JaCoCo | LGPL-2.1 (только инструмент сборки, не поставляется) / Apache / EPL | |
| Тесты | JUnit 5, AssertJ, Mockito, Testcontainers (Postgres, Kafka) | EPL/Apache/MIT | |

## Backend (Python) — сервис face
| Что | Выбор | Лицензия |
|---|---|---|
| Рантайм | Python 3.12, `uv` | PSF / MIT |
| API | FastAPI + uvicorn + pydantic v2 | MIT / BSD |
| Инференс | onnxruntime (CPU), opencv-python-headless, numpy | MIT / Apache-2.0 / BSD |
| Модели | **YuNet** (детекция, MIT), **SFace** (эмбеддинг 128-d, Apache-2.0), **MiniFASNet** (антиспуфинг, Apache-2.0). InsightFace — **нельзя** (веса non-commercial) | |
| Качество | ruff, mypy --strict, pytest | MIT |

## Frontend — правила в скилле `frontend-fsd`
| Что | Выбор | Лицензия |
|---|---|---|
| Сборка | **одно** Vite-приложение, React 19, TypeScript strict, **npm**, Feature-Sliced Design | MIT |
| UI | Tailwind CSS 4 + shadcn/ui (Radix, cva), lucide-react, recharts | MIT |
| Данные | axios (`$api` с refresh-интерсептором), TanStack Query, типы из `openapi-typescript` | MIT |
| Состояние / формы | zustand, react-hook-form + zod | MIT |
| i18n / уведомления | i18next (namespace на страницу), react-hot-toast | MIT |
| Роутинг | React Router, зоны `/kiosk`, `/worker`, `/admin` | MIT |
| OIDC | `oidc-client-ts` (токены в памяти) | Apache-2.0 |
| QR | `qrcode.react` (показ), `@zxing/browser` (скан) | ISC / Apache-2.0 |
| PWA | `vite-plugin-pwa` (`scope: /worker/`) | MIT |
| Моки | MSW — только `import.meta.env.DEV` | MIT |
| Качество | ESLint (import/order, tanstack-query), Prettier (+tailwind), husky + lint-staged, `fsd-check.mjs` | MIT |

## LLM
| Режим | Провайдер | Модель |
|---|---|---|
| Облако (по умолчанию на защите, если дадут ключ) | **GigaChat API** (на хакатоне Сбера это плюс) | GigaChat-2 / Pro |
| Облако-альтернатива | OpenAI-совместимый endpoint | любой |
| **Локально (обязательно, Д6)** | **Ollama** в docker compose (профиль `llm-local`) | `qwen3:4b` (Apache-2.0), при наличии GPU `qwen3:8b` |

Переключение: `LLM_PROVIDER=gigachat|openai|ollama` в `.env`. Код не меняется.

## Инфраструктура
- `docker compose`: `caddy`, `frontend`, `gateway`, `people`, `access`, `face`, `shift`, `assistant`, `keycloak`, `postgres`, `kafka`, `jaeger`, `ollama` (профиль `llm-local`), `prometheus` (опционально). Сервер и CI/CD — DEPLOY.md.
- **HTTPS обязателен**: камера и геолокация работают только в secure context. Варианты: (1) VPS + домен + Let's Encrypt через Caddy; (2) Caddy `tls internal` в локальной сети площадки + корневой сертификат на телефоны; (3) туннель. Решить до хакатона (QUESTIONS.md).
- **Работа без интернета** (ADR-028/029): QR генерируется на телефоне без сети; весь стек (включая LLM через Ollama) может стоять на сервере в локальной сети площадки.
- Секреты: `infra/.env` из `scripts/gen-env.sh`, в git только `.env.example` (`NAME=...`); gitleaks в pre-commit и CI.
- CI: GitHub Actions — матрица по изменённым сервисам (Gradle `check` с Postgres service container, Python `ruff/mypy/pytest`), frontend `npm ci && lint && typecheck && build && fsd-check`, gitleaks, guard, trivy.

## Сознательно НЕ берём
- RabbitMQ (заменён Kafka, ADR-024), Redis (AGPL/SSPL), Kubernetes и service mesh.
- Spring Cloud Config Server и Vault (ADR-027): на один сервер и один день — лишние точки отказа.
- MinIO (AGPL), Grafana (AGPL), iText (AGPL): лицензии. Фото и эмбеддинги — в Postgres (`bytea`).
- Qdrant/векторные БД: сверка 1:1, векторов сотни — косинус в numpy (ADR-025).
- InsightFace: веса только для некоммерческого использования.
- JPA/Hibernate: jOOQ + Liquibase, как в эталонах команды.
