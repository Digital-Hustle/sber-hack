# Журнал решений (ADR-lite)

Формат: `ADR-NNN | дата | статус | решение | контекст | альтернативы | последствия`. Добавляем при каждом значимом выборе: технологии, зависимости (с лицензией), отступлении от плана.

### ADR-001 Модульный монолит на Spring Boot — ЗАМЕНЕНО ADR-015
Контекст: 1 день, 3 бэкендера. Решение: один сервис, модули — пакеты с `api`-границами, проверка ArchUnit. Альтернативы: микросервисы — отвергнуто (интеграционные риски). Последствия: один деплой, простой compose.

### ADR-002 Сверка лица в JVM: OpenCV YuNet + SFace — ЗАМЕНЕНО ADR-025
Контекст: нужна настоящая сверка 1:1, без вирусных лицензий, без второго языка. Решение: `org.openpnp:opencv`, модели из opencv_zoo (YuNet — MIT, SFace — Apache-2.0). Порог cos — из настроек, калибруется на команде (стартовое значение 0.363 по документации OpenCV). Альтернативы: InsightFace (некоммерческие веса), Python-сервис (план Б), облачные API (ПДн уходят наружу).

### ADR-003 Динамический пропуск: серверный одноразовый токен — ЗАМЕНЕНО ADR-028
Решение: телефон получает от сервера токен `{nonce, workerId, deviceId, exp}` + HMAC, TTL 30 с, гасится атомарным UPDATE. Альтернативы: TOTP на устройстве (секрет на клиенте, сложнее отзыв), статичный QR (снятие с конкурса).

### ADR-004 Активная живость через случайный челлендж
Решение: сервер выдаёт случайное действие, по 5 точкам YuNet оцениваем поворот головы (yaw) на кадрах и проверяем, что во всех кадрах тот же человек. Против распечатки и фото на экране. Ограничение: видео с поворотом головы не отсекается — честно говорим об этом на защите, улучшение — MiniFASNet.

### ADR-005 LLM за портом, три адаптера, локально Ollama
Решение: `LlmGateway` (паттерн `...Gateway` из certis-api; было `ChatModelPort`), адаптеры GigaChat / OpenAI-compatible / Ollama; выбор через `LLM_PROVIDER`. Локальная модель `qwen3:4b` (Apache-2.0) — компромисс скорости на CPU и качества function calling.

### ADR-006 Помощник только через инструменты + валидатор чисел
Решение: модель не видит БД, только результаты фиксированного набора серверных инструментов; ответ проверяется на числа. Против галлюцинаций (основание для снятия).

### ADR-007 Демо-данные только через доменный сервис с пометкой
Решение: генератор истории (если нужен для графиков) пишет события через тот же сервис, что и реальные проходы, с `source=SIMULATOR`; фильтр в UI, на защите говорим об этом открыто.

### ADR-008 SSE вместо WebSocket
Решение: однонаправленный поток обстановки и оповещений; проще и стабильнее через Caddy/nginx.

### ADR-009 Фото и эмбеддинги в PostgreSQL
Решение: `bytea`/`real[]`. Альтернатива MinIO — AGPL; файлы на диске — риск «файл рядом с кодом».

### ADR-010 Не микросервисы — ЗАМЕНЕНО ADR-015
Решение: модульный монолит + инфраструктурные сервисы (Keycloak, PostgreSQL, Ollama, Caddy). Причина: атомарность «гашение токена + решение + журнал» в одной транзакции — ключевое свойство безопасности; микросервисы дают распределённые транзакции и межсервисную аутентификацию без баллов. Модули выносимы благодаря ArchUnit-границам.

### ADR-011 Keycloak для персонала, ключ устройства для рабочих
Решение: персонал (ADMIN/MANAGER/SECURITY_OFFICER/GUARD) — Keycloak SSO + TOTP; рабочие — беспарольный вход ключом ECDSA устройства, backend выпускает device-токены ES256; resource server принимает двух издателей. Альтернатива «все в Keycloak» — рабочим нужны пароли, которые на стройке передают друг другу.

### ADR-012 Миграции с меткой времени (Flyway) — ЗАМЕНЕНО ADR-023, идея метки времени сохранена
Решение: `V<yyyyMMddHHmmss>__slug.sql` через `scripts/new-migration.sh <service> <slug>`, у каждого сервиса свои миграции, `flyway.out-of-order=true`. Причина: 3 бэкендера и агенты параллельно открывают PR — последовательные номера конфликтуют.

### ADR-013 Контракт API — snapshot `contracts/openapi/<svc>.json` в git
Решение: backend генерирует OpenAPI, snapshot коммитится и сверяется в CI; TS-клиент генерируется при сборке и не коммитится. Причина: изменения контракта видны в диффе PR, нет конфликтов сгенерированного кода.

### ADR-014 Агенты работают только через PR (мерж агентом — по явному делегированию, ADR-037)
Решение: скилл `proxodnaya-dev`, скрипты verify/guard/open-pr, CODEOWNERS, branch protection. Мерж — только человек. Причина: Д7 (история), Д11 (каждый понимает свой код), защита от красных линий.

### ADR-015 Микросервисы
Контекст: команда хочет показать распределённую архитектуру. Решение: 6 сервисов + gateway (см. ARCHITECTURE.md). Гашение токена, решение и журнал не делим — они внутри access-service в одной транзакции. Синхронные вызовы только в пути прохода, с таймаутом и fail-closed; остальное — события. Риски: интеграция и отладка → смягчаем общей библиотекой `libs/common`, контрактами в `contracts/`, трейсингом Jaeger, интеграцией каждые 2 часа.

### ADR-016 RabbitMQ + transactional outbox — брокер ЗАМЕНЁН ADR-024, outbox сохранён
Решение: RabbitMQ (MPL-2.0, ~150 МБ RAM) вместо Kafka (тяжелее) и Redis (лицензия AGPL/SSPL с 2024). Outbox-таблица в БД продюсера + публикатор по расписанию; потребители идемпотентны по `eventId`.

### ADR-017 Database-per-service в одном PostgreSQL
Решение: один инстанс PostgreSQL, отдельная БД и роль на сервис. Изоляция данных без 7 контейнеров БД.

### ADR-018 Межсервисная аутентификация через Keycloak service accounts
Решение: client credentials на каждый сервис, `aud` целевого сервиса; каждый сервис сам валидирует JWT. Внутренняя сеть без проброса портов.

### ADR-019 Трейсинг OpenTelemetry + Jaeger
Решение: Micrometer Tracing → OTLP → Jaeger all-in-one (Apache-2.0). Один проход = один трейс через все сервисы.

### ADR-020 Фронтенд: одно приложение FSD с тремя зонами
Дата: 08.10.2026. Решение: одно Vite-приложение по Feature-Sliced Design (эталон команды `MelevaTe/hackathon-spring-2026-frontend`), зоны `/kiosk`, `/worker`, `/admin` с ленивой загрузкой и ролевыми guard'ами. Контекст: два фронтенда, общий код (entities, ui, api) в трёх workspace-пакетах дублировал бы инфраструктуру. Альтернативы: pnpm workspaces с тремя приложениями — больше конфигов и сборок. Последствия: один бандл, один деплой, `fsd-check.mjs` в CI; PWA со `scope: /worker/`.

### ADR-021 Фронтенд-стек по эталону команды
Решение: npm, React 19, Tailwind 4 + shadcn/ui, axios, TanStack Query, zustand, react-hook-form + zod, i18next, react-hot-toast вместо Mantine/pnpm/openapi-fetch. Отличия от эталона: токены только в памяти (oidc-client-ts), MSW только в dev, тексты уведомлений через i18n, нет импортов из `app` в нижние слои. Причина: команда на этом стеке уже выиграла хакатон, агенты знают shadcn.

### ADR-022 Сборка бэкенда: Gradle Kotlin DSL + version catalog + convention plugins
Решение: `services/` — одна Gradle-сборка: авто-include папок (паттерн sueta-rnd-back), `gradle/libs.versions.toml`, `build-logic` с плагинами `proxodnaya.java/.service/.jooq/.gateway/.library`, один wrapper (Gradle 9.3), checkstyle из sueta. Альтернативы: Maven multi-module (команда пишет на Gradle), свой wrapper на сервис (как в sueta — разъезжаются версии). Проверено: скелет `init-backend.sh` + `new-service.sh` собирается (`check`, Liquibase update, jOOQ codegen, bootJar).

### ADR-023 jOOQ + Liquibase вместо JPA + Flyway
Решение: доступ к БД через jOOQ (codegen из живой схемы после `update`), миграции — Liquibase formatted SQL, `includeAll`, имена `<yyyyMMddHHmmss>-slug.sql`, `--rollback` обязателен, демо-данные `context:dev`. Причина: оба эталона команды (sueta, certis) на jOOQ + Liquibase; запросы журнала и аналитики проще в SQL. Последствия: компиляции нужна БД (локально compose, в CI — service container), поэтому Docker-образ упаковывает готовый jar.

### ADR-024 Kafka вместо RabbitMQ
Решение: Apache Kafka 3.9 в KRaft-режиме (без ZooKeeper), один брокер, Spring Kafka. Топики `<владелец>.<агрегат>.v<N>`, ключ = id агрегата, outbox-relay с `acks=all` и идемпотентным продюсером, потребители идемпотентны (`processed_events`), DLT после 3 повторов. Причина: решение команды; переигрываемый журнал событий (shift и assistant пересчитывают состояние с offset 0). Цена: ~1 ГБ RAM, укладывается в 32 ГБ сервера.

### ADR-025 Сервис face на Python
Решение: FastAPI + onnxruntime + opencv-python-headless; YuNet (MIT), SFace (Apache-2.0), MiniFASNet (Apache-2.0). Сверка 1:1: QR определяет рабочего, кадр сравнивается косинусом с 3–5 его эталонами (numpy), порог из конфига. Векторная БД (pgvector/Qdrant) не нужна: поиска 1:N нет, векторов сотни; pgvector — только если появится вход «только по лицу». Эмбеддинги шифруются AES-GCM. InsightFace запрещён (веса non-commercial). Причина: модели и CV на Python быстрее отлаживаются, чем OpenCV в JVM.

### ADR-026 Внутреннее устройство сервиса по certis-api
Решение: `api/controller` (интерфейс + `impl`), `api/dto/{request *Rq, response *Rs}`, `features/<f>/{command, query, application, api, spi, gateway, validator}`. Фичи общаются только через `api`/`spi`, внешние системы — через `...Gateway`, коллекции — в обёртках, `ResponseEntity` — только для динамических заголовков. Проверка: ArchUnit-тест из шаблона + `be-check.mjs`. Отличия от certis: Java, микросервисы, JWT Keycloak.

### ADR-027 Без Spring Cloud Config Server и Vault
Решение: конфиг в `application.yml` сервиса, секреты из env (`infra/.env` от `scripts/gen-env.sh`). Причина: в sueta Config Server с `fail-fast` — единая точка отказа старта, Vault в dev-режиме теряет секреты при рестарте и держит root-token в git. На одном сервере это лишние движущиеся части.

### ADR-028 Офлайн-QR, подписанный ключом устройства
Решение: телефон без сети раз в 30 с формирует `v1.<deviceId>.<window>.<sig>`, `sig = ECDSA P-256` неизвлекаемым ключом устройства (WebCrypto, IndexedDB). Access проверяет подпись по публичному ключу (проекция `people.device.v1`), окно ±90 с и гасит `(device_id, window)` вставкой с `on conflict do nothing` в одной транзакции с решением. Заменяет ADR-003. Альтернативы: серверный токен (телефону нужна сеть), TOTP/HMAC (общий секрет на сервере и киоске — утечка БД = подделка любых QR). Последствия: стройка без мобильного интернета не мешает проходу; скриншот живёт ≤ 30 с и одноразов; плюс сверка лица.

### ADR-029 Офлайн-режим киоска = ручной проход охранника
Решение: флаг `OFFLINE_MODE_ENABLED` (по умолчанию `false`). Киоск хранит подписанный сервером снимок допусков (TTL 12 ч); без связи с access проверяет подпись QR, показывает фото, а **решение принимает охранник** (ручной проход GUARD, `source=OFFLINE`). Журнал в IndexedDB с hash-chain досылается после восстановления связи, конфликты anti-passback — в админку. Причина: Д2 («решение принимает бэкенд») не нарушается — клиент не ставит `ALLOW` сам. На защите показываем только по запросу.

### ADR-030 6 сервисов: notify влит в shift
Решение: gateway, people, access, face, shift, assistant. SSE-хаб и правила оповещений — в shift (они и так строятся по интервалам). Причина: меньше контейнеров и контрактов при тех же границах данных; три бэка — по два сервиса.

### ADR-031 Дизайн-система: фирменный стиль Сбера + только Motion + кастомные контролы из shadcn-реестров — политика анимаций и следование Nova ЗАМЕНЕНЫ ADR-036
Решение: тема на цветах брендбука Сбера (`#21A038`, мастер-градиент) и шкалах открытой Plasma (MIT), шрифты SB Sans Text/Display с CDN Сбера (в git не кладём). Все анимации — только `motion` (`motion/react`). GSAP, Lenis, react-spring, anime.js, `framer-motion`, `tw-animate-css` и CSS `@keyframes` запрещены, WebGL — только по отдельному ADR. Это проверяет `fsd-check` (правила `banned-import` и `css-animation`). Каждый контрол кастомный: поведение от Radix/cmdk, вид и движение из реестров Animate UI, Motion Primitives, Magic UI, Aceternity, React Bits (только motion-компоненты), Eldora, Animata, Cult UI, Smooth UI, Evil Charts, Lucide Animated, доводка по чек-листу `frontend-fsd/references/custom-controls.md`. Лицензии Aceternity License и MIT + Commons Clause (React Bits, Animate UI) допускаем для UI-компонентов внутри продукта: они не копилефтные и не запрещают публичный репозиторий, запрещают только продавать или распространять сами компоненты отдельно. Картинки только реальные или сгенерированные по запросу к человеку, логотип Сбера — только официальный файл с разрешения. Причина: единый движок анимаций (меньше бандл, одни токены, прерываемость), узнаваемый бренд организатора, «вау» без риска по лицензиям.

### ADR-032 Сторонние скиллы и MCP для агентов в репозитории
Решение: в `.agents/skills/` лежат копии проверенных скиллов (MIT/Apache) с блоком «Проходная» и `UPSTREAM.md`: shadcn, frontend-design, design-taste-frontend, скиллы анимаций Эмиля Ковальски, webapp-testing, TDD/отладка/проверка из superpowers, springboot-security, find-skills. MCP-серверы `shadcn`, `magicui`, `21st` прописаны в `.mcp.json`, `.cursor/mcp.json` и `.codex/config.toml`. Ключ 21st — только в окружении (`API_KEY_21ST`). Причина: агенты всей команды одинаково ищут и ставят компоненты, а не пишут их с нуля. Копия вместо ссылки — воспроизводимость в день хакатона без сети к skills.sh. Матрица «роль → скиллы» — `docs/AGENT_SKILLS.md`.

### ADR-033 Единый источник дизайн-токенов + запрет хардкода стилей
Решение: все цвета, шрифты, размеры текста, отступы, высоты контролов, радиусы, тени, z-слои и параметры движения задаются в одном typed-файле `frontend/src/shared/config/tokens.ts` (шаблон — `.agents/skills/frontend-fsd/assets/tokens.ts`). `gen-tokens.mjs` генерирует из него `src/app/styles/theme.css` (Tailwind 4 `@theme` + shadcn-переменные, дефолтные палитра/размеры/анимации Tailwind выключены) и таблицы в `frontend/docs/DESIGN.md`, проверяя контраст WCAG. Motion берёт значения через `shared/config/motion.ts`. `fsd-check` запрещает hex/rgb, произвольные значения Tailwind, отступы вне сетки, числа в `transition` и размеры в `style`; `guard.sh`/`verify.sh` падают, если тема устарела. Значения — Сбер Nova (кнопка `#148F2B`, шкала 13/16…37/44) + брендбук + Plasma; радиусы — свои, ADR-036. Причина: пять человек и их агенты иначе разъезжаются по оттенкам и отступам; одна правка токена меняет всё приложение и документацию. Компромисс: `primary` как в Nova даёт 4.2:1 с белым — текст кнопок semibold 16+, мелкий зелёный текст только `accent-foreground` (зафиксировано в `contrastLargeTextOnly`).

### ADR-034 Документы и память на каждый модуль
Решение: у каждого сервиса и фронтенда свой `AGENTS.md` (короткий, важнее корневого для файлов модуля; `CLAUDE.md → @AGENTS.md`) и `docs/PRD.md`, `ARCHITECTURE.md`, `RULES.md`, `TASKS.md`, `MEMORY.md` (у фронта ещё `DESIGN.md`). Модульные документы не повторяют общие, а ссылаются на них. `MEMORY.md` по мотивам Cline Memory Bank: перезаписываемое «текущее состояние», карта, решения, баги и append-only журнал (`merge=union` в `.gitattributes`). Корневые `docs/PRD.md`, `TASKS.md` (вехи, платформа, совместные задачи), `MEMORY.md` ведёт B1. Причина: агенты меняются между сессиями, а git — единственная общая память команды; узкий контекст модуля дешевле и точнее общего.

### ADR-035 Мультиагентный процесс: draft PR как заявка и handoff, локальный CI
Решение: одна задача = ветка = worktree = draft PR, открытый сразу (`scripts/task-start.sh`); статус задачи меняется в той же ветке. Коммиты и push после каждого зелёного шага, раздел Handoff в описании PR перед сменой сессии (`scripts/handoff.sh`), финиш — `scripts/open-pr.sh` (draft → ready). Ревью — отдельная сессия/другой агент + человек; мержит только человек, по одному PR. Сгенерированные файлы при конфликте перегенерируются. `scripts/ci-local.sh quick|full|act` запускает те же проверки, что GitHub Actions (Testcontainers, gitleaks в docker, опционально nektos/act). Причина: 5 человек × 1–2 агента без явного протокола теряют работу при обрыве сессии, дублируют задачи и ждут CI. Подробно — `docs/WORKFLOW.md`, роли и need — `docs/ROLES.md`.

### ADR-036 Ревизия дизайна: от Сбера — цвет и шрифт, анимации — Motion + WebGL/GSAP по месту
Решение: обязательны только фирменный зелёный Сбера и SB Sans Text/Display. Nova — стартовая точка (публично доступна лишь часть, полный брендбук закрыт для внешних), остальное — своя современная система: крупные радиусы (кнопки и инпуты 14, карточки 20, шторки 28), свои вспомогательные цвета, всё в `tokens.ts`. Иконки — Plasma Icons (MIT, открытые иконки Сбера) как SVG в `shared/ui/Icon`, недостающие — lucide. Логотип — заглушка до официального. Анимации: основной движок UI — `motion/react`; разрешены WebGL (`three`, `@react-three/fiber`, `@react-three/drei`, `@react-three/postprocessing`, `postprocessing`, `ogl`) в любых зонах — устройства потянут, цель — красивый продукт; GSAP (бесплатная Standard License, не GPL) и Lenis (MIT) — для компонентов из реестров и сложных таймлайнов/скролла, не смешивая два движка в одном компоненте. Под запретом остаются дубли без пользы: пакет `framer-motion` (используем `motion`), `react-spring`, `animejs`, `popmotion`; CSS `@keyframes`/`animate-*` — только внутри `shared/ui` (код реестров). Референсы (21st.dev, Aceternity, React Bits и др.) агент открывает в браузере, снимает скриншоты состояний, разбирает их зрением и кодом и повторяет на наших токенах; MCP 21st убран (платный ключ). Причина: цель — сильный визуал, а не формальное следование закрытому гайдлайну; запреты оставлены только там, где они защищают от дублей.

### ADR-037 Мерж агентом по явному делегированию
Решение: человек-владелец зоны может явно разрешить агенту мержить PR в этой зоне (сейчас: документация, скиллы и старт разработки — делегировал miroslavrov). Агент мержит только после зелёного CI и самопроверки по `pr-workflow.md`, по одному PR, коммиты — от имени того человека, чей это агент. Делегирование фиксируется в `docs/MEMORY.md` §3. Причина: в подготовительной фазе нет второго ревьюера, а ожидание мержа блокирует следующие шаги.

### ADR-038 Rate-limit в gateway — окно в памяти
Решение: перебор инвайтов (`/people/api/v1/auth/**`) и попытки киоска ограничивает собственный `WebFilter` gateway: фиксированное окно на адрес клиента (последний хоп `X-Forwarded-For` от Caddy), `RATE_LIMIT_CAPACITY` запросов за `RATE_LIMIT_WINDOW`, сверх — `429 RATE_LIMITED` + `Retry-After`. Без Redis, Bucket4j и Resilience4j. Причина: gateway один, счётчики при рестарте можно терять, лишняя инфраструктура не окупается. При нескольких экземплярах — перейти на `RequestRateLimiter` SCG с Redis.

### ADR-039 Keycloak под `/auth` того же хоста, ключи — из внутренней сети
Решение: Caddy отдаёт Keycloak по `https://<host>/auth` (`KC_HTTP_RELATIVE_PATH=/auth`, `KC_HOSTNAME=https://<host>/auth`), без поддомена `auth.`. `iss` в токенах — публичный адрес, а JWKS и токены client credentials сервисы берут напрямую `http://keycloak:8080/auth/...` (`OIDC_JWKS_URI`, `OIDC_TOKEN_URI`), сверяя `iss` с `OIDC_ISSUER_URI`. Причина: один сертификат и один origin (нет CORS к Keycloak, проще CSP), стенд работает на `localhost` и на ноутбуке без DNS, контейнерам не нужен доступ к публичному адресу и доверие к сертификату Caddy.

### ADR-040 people — второй издатель JWT (ES256): телефоны и персонал
Решение: people выпускает JWT ES256 ключом `DEVICE_TOKEN_SIGNING_KEY` (P-256, PKCS#8, один на все экземпляры) с `iss = https://<host>/people`, `aud = people, access, shift, assistant`, ролями в `realm_access.roles` (как у Keycloak). Открытые ключи — `GET /internal/people/jwks` (только внутренняя сеть). Gateway и сервисы принимают двух издателей (`TrustedIssuers`: менеджер аутентификации по `iss`, у каждого — свой JWKS, алгоритм, проверка `iss`/`exp`/`aud`); токен другого издателя отклоняется. Телефон получает device-токен (10 мин, роль WORKER, claim `did`) подписью одноразового challenge ключом, привязанным при активации (PPL-05); тем же механизмом people выдаёт сессии персонала по коду из письма (PPL-13). Keycloak остаётся издателем сервисных аккаунтов. Причина: телефону не нужен Keycloak-аккаунт, ключ устройства уже доказывает владельца; одна схема проверки для всех сервисов, без общих секретов и без обращения к people на каждый запрос.

### ADR-041 Персонал входит по коду из письма, без пароля
Решение: вход в кабинет и на киоск — почта → 6 цифр из письма (10 мин, 5 попыток, повтор через 45 с, только HMAC с солью) → сессия 12 ч в people: access-токен ES256 10 мин (ADR-040) + одноразовый refresh. Персонал и роли (ADMIN, SECURITY_OFFICER, MANAGER, INSTALLER, GUARD) хранит people (`staff_users`), администратор приглашает и отключает сотрудников, последнего администратора разжаловать или отключить нельзя (`LAST_ADMIN`), всё пишется в журнал доступа. Первый администратор — `STAFF_BOOTSTRAP_ADMIN_EMAIL`. Письма — SMTP (`SMTP_*`). Это заменяет пароль + TOTP Keycloak из FR-62: фактор владения — почтовый ящик, перебор закрыт лимитом попыток, сгоранием кода и rate-limit gateway; ответ на запрос кода одинаков для любого адреса. Keycloak остаётся для сервисных аккаунтов (FR-60). Причина: фронт (песочница, ADR-046) сделан под беспарольный вход; пароли персонала на стройке забывают и передают так же, как пароли рабочих; отключение действует сразу для people и не позже 10 мин для остальных сервисов.

### ADR-042 Общий SMTP-клиент всех кейсов, без общего хранилища пользователей
Решение: `shared/mail` — единственный клиент SMTP ящика `no-reply@digital-hustle.ru` на общем сервере. Он принимает внутренние запросы только с сервисным токеном, формирует HTML и plain-text для каждого кейса, отправляет через TLS. Учётные записи, роли, генерация/хэширование/гашение кодов и сессии остаются ответственностью каждого кейса: общий отправитель не даёт доступ к другому кейсу. SMTP-реквизиты — только `/etc/digital-hustle/mail.env`, не дублируются в git или браузере. Без SMTP отправка возвращает 503, не имитирует успех. В «Проходной» используется `SharedStaffMailer`, включаемый `SHARED_MAIL_ENABLED`; остальные кейсы подключаются после появления собственного серверного email-входа. Python 3.12 + FastAPI (MIT) + Uvicorn (BSD-3-Clause), SMTP и MIME — стандартная библиотека Python (PSF). Причина: один почтовый ящик, единое оформление и одна точка настройки без передачи пароля каждому кейсу.

### ADR-043 Коды терминала не являются авторизацией прохода (ACC-19)
Два глобальных кода `service`/`guard` хранит access: PBKDF2-HMAC-SHA256 (JCA Java 21, 120000 итераций, соль 16 случайных байт), только хэш, длина, момент и автор из JWT. Слабые сочетания и заводской код отвергаются сервером. ADMIN меняет оба, INSTALLER только service, SECURITY_OFFICER только guard. Кабинет получает метаданные без хэша; подписанный `/kiosk/me` — хэши только после сопряжения с существующей проходной. Онлайн-панель берёт хэш из этого ответа, офлайн — только из подписанного снимка (ACC-15 ещё не реализован). После сопряжения заводской сервисный PIN не работает. Коды открывают локальную панель, но не дают роль GUARD, не обходят QR/лицо и не меняют решение сервера; ручной онлайн-проход по-прежнему требует GUARD JWT. Новых библиотек нет.

## Лицензии зависимостей
| Зависимость | Лицензия | Кто добавил |
|---|---|---|
| Spring Boot | Apache-2.0 | B1 |
| PostgreSQL | PostgreSQL | B1 |
| onnxruntime / opencv-python-headless / FastAPI | MIT / Apache-2.0 / MIT | B2 |
| YuNet / SFace | MIT / Apache-2.0 | B2 |
| Ollama / Qwen3 | MIT / Apache-2.0 | B3 |
| Apache POI | Apache-2.0 | B3 |
| Keycloak | Apache-2.0 | B1 |
| Spring Cloud Gateway | Apache-2.0 | B1 |
| Apache Kafka / Spring Kafka | Apache-2.0 | B1 |
| jOOQ (OSS) / Liquibase / MapStruct | Apache-2.0 | B1 |
| Gradle / checkstyle / ArchUnit | Apache-2.0 / LGPL-2.1 (инструмент) / Apache-2.0 | B1 |
| Jaeger / OpenTelemetry | Apache-2.0 | B1 |
| oidc-client-ts / react-oidc-context | Apache-2.0 / MIT | F1/F2 |
| React / Tailwind / shadcn / Radix / TanStack / zustand / i18next | MIT | F1/F2 |
| motion (бывший Framer Motion) | MIT | F1/F2 |
| Компоненты Magic UI / Motion Primitives / Eldora UI / Animata / Cult UI / Smooth UI / Evil Charts / Lucide Animated | MIT | F1/F2 |
| Компоненты React Bits / Animate UI | MIT + Commons Clause (ADR-031) | F1/F2 |
| Компоненты Aceternity UI (free) | Aceternity License, бесплатно для конечных продуктов (ADR-031) | F1/F2 |
| Компоненты Uiverse | MIT | F1/F2 |
| input-otp / react-dropzone / react-day-picker / cmdk / @tanstack/react-table / @tanstack/react-virtual | MIT | F1/F2 |
| Палитра Plasma (salute-developers/plasma) | MIT | F2 |
| Шрифты SB Sans Text / Display (CDN Сбера, не в репо) | шрифт Сбера (ParaType), используем для продукта на хакатоне Сбера | F2 |
| Скиллы агентов (shadcn, superpowers, emilkowalski, taste-skill, ecc, find-skills / anthropics) | MIT / Apache-2.0 | B1 |
| MCP shadcn / @magicuidesign/mcp | MIT / ISC | B1 |
| nektos/act (локальный прогон GitHub Actions, инструмент разработчика, не поставляется) | MIT | B1 |
| three / @react-three/fiber / @react-three/drei / postprocessing / ogl | MIT / MIT / MIT / Zlib / Unlicense (ADR-036) | F1/F2 |
| GSAP | Standard «no charge» License (бесплатно, не GPL; ADR-036) | F1/F2 |
| Lenis | MIT | F1/F2 |
| Plasma Icons (@salutejs/plasma-icons, SVG) | MIT | F2 |
| spring-boot-starter-mail (Spring Mail + Eclipse Angus Mail) | Apache-2.0 / EPL-2.0 (Angus, вариант GPL-2.0 with Classpath Exception) | B1 |
| Uvicorn / Python stdlib SMTP (shared/mail, ADR-042) | BSD-3-Clause / PSF | B1 |
