# RULES — правила для AI-агентов во frontend

Сверху действуют `../../rules.md` (красные линии) и скилл `frontend-fsd`. Здесь — конкретно для нашего фронта. Нарушение пунктов с ⛔ = PR не принимается.

## Делай
- Работай в своей зоне и своём слайсе (владельцы — `../AGENTS.md`). `shared`/`entities`/`app` — PR с ревью F2.
- Сначала найди существующее: `grep -r` по `shared/ui`, `entities`, `features` — не создавай второй Button/Select/формат даты.
- Данные: `entity/api/<x>Api.ts` → `queries.ts` (фабрика ключей) → компонент. Запись — `features/<verb-noun>/api/mutations.ts` + `invalidateQueries`.
- Каждый экран: skeleton / empty / error с «Повторить» / data / offline-баннер.
- Тексты — `t("key")`, ключи сразу в `public/locales/ru`.
- Стили — только классы токенов из `docs/DESIGN.md`. Нужного токена нет → добавь его в `shared/config/tokens.ts`, `npm run gen:tokens`, опиши роль токена (PR с ревью F2).
- Анимации UI — `motion/react` и пресеты `@/shared/config/motion`; 3D/шейдеры — R3F/ogl, сложные таймлайны — GSAP, параметры тоже из токенов (`tween.*`, `spring.*`, `fadeUp`, `press`, `inView`).
- Перед PR: `proxodnaya/scripts/ci-local.sh quick`, руками проверь экран в браузере (desktop + 390×844), опиши шаги в PR.

## Избегай
- ⛔ Логики решения о проходе на клиенте (`decision = "ALLOW"`, вывод ALLOW из локальных проверок) — Д2, снятие.
- ⛔ Токенов в localStorage/sessionStorage; приватного ключа устройства в извлекаемом виде.
- ⛔ Моковых данных и MSW вне `src/mocks` и `import.meta.env.DEV`; «красивых» графиков на пустой БД.
- ⛔ hex/rgb в коде, `p-[13px]`/`text-[#fff]`/`w-[420px]`, отступов вне сетки, `duration: 0.3`/`ease: [...]` литералами, `style={{ padding: 12 }}` — правила `fsd-check` (ADR-033).
- ⛔ Дублирующие движки: `framer-motion` (есть `motion`), `react-spring`, `anime.js`, `popmotion`; CSS `@keyframes`/`animate-*` вне `shared/ui`. WebGL (three/R3F/ogl), GSAP, Lenis — можно, один движок на компонент (ADR-036).
- Ручного `fetch`/`axios` в компонентах; копирования серверных данных в zustand; `useEffect` для загрузки данных.
- Глубоких импортов в чужой слайс, импортов вверх по слоям, `@x`-нотации.
- Нативных `<select>`, `<input type=date>`, браузерных скроллбаров и `alert/confirm` — только кастомные контролы (`frontend-fsd/references/custom-controls.md`).

## Библиотеки
Разрешены: из `docs/ARCHITECTURE.md §Стек` + компоненты из реестров `components.json` (Magic UI, Animate UI, Aceternity, React Bits (включая gsap/ogl/three), Motion Primitives, Eldora, Cult UI, Smooth UI, Evil Charts, Lucide Animated) — через `npx shadcn add`, с адаптацией под токены. Новая npm-зависимость → проверь лицензию (MIT/Apache/BSD/ISC; GPL/AGPL нельзя), строка в `../../docs/DECISIONS.md`, ревью F2 (`package.json` — его).

## Обработка ошибок
- Ответ ошибки — `application/problem+json` (`shared/api/problem.ts` → `{code, detail, hint, errors[]}`); показываем `detail`/`hint` с бэка или `t("errors.<code>")`, никогда сырой stack/JSON.
- 401 → refresh в интерсепторе `$api`, повтор один раз, затем на вход. 403 → `ForbiddenPage`. 404 → empty-состояние. 5xx/сеть → error-состояние с «Повторить» + toast.
- Отказ прохода — это не ошибка UI: `DecisionScreen` показывает код/текст/подсказку из ответа access (таблица кодов — `../../docs/API.md`).
- Мутации: кнопка в `loading`, повторное нажатие заблокировано, `Idempotency-Key` для создания (frontend-contract.md).
- Глобальный `ErrorBoundary` на зону: экран «Что-то пошло не так» + «Перезагрузить», ошибка в консоль с `traceId` из заголовка ответа.

## Ограничения
- Камера и геолокация — только HTTPS (secure context). Проверяем на iOS Safari и Android Chrome.
- Киоск — Chrome, полный экран, тач-цели ≥ `h-control-xl`; телефон — ≥ `h-control-md`.
- Бандл зоны грузится лениво; `/worker` открывается и показывает QR без сети (precache PWA).
- Продуктовый код — только в день хакатона (п. 7.3 положения); до этого — только документы и скиллы.
