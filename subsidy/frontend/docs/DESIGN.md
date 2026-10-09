# DESIGN — дизайн-система «Субсидий»

**Главное правило: стиль задаётся в одном месте.** Все цвета, шрифты, размеры текста, отступы, радиусы, тени, слои и параметры анимаций живут в `src/shared/config/tokens.ts` (до хакатона — шаблон `.agents/skills/frontend-fsd/assets/tokens.ts`). Из него `npm run gen:tokens` генерирует `src/app/styles/theme.css` (Tailwind 4 + shadcn) и таблицы в разделе 2 этого файла. Motion читает те же значения через `src/shared/config/motion.ts`. Литералы в коде ловит `fsd-check`, устаревшую тему — `guard.sh`/`verify.sh` (ADR-033).

Ниже в тексте — только **имена токенов**, значения смотри в таблицах раздела 2 (они сгенерированы и не расходятся с кодом).

## 0. Как поменять или добавить токен
1. Правь `tokens.ts` (владелец F2; F1 — через PR с ревью F2). Новому токену — комментарий «для чего».
2. `npm run gen:tokens` (до хакатона: `node --experimental-strip-types .agents/skills/frontend-fsd/scripts/gen-tokens.mjs .agents/skills/frontend-fsd/assets/tokens.ts --css .agents/skills/frontend-fsd/assets/theme.css --md subsidy/frontend/docs/DESIGN.md`).
3. Генератор проверит контраст пар `x / x-foreground` (WCAG ≥ 4.5:1; исключения — `contrastLargeTextOnly` с причиной).
4. Закоммить `tokens.ts` + `theme.css` + `DESIGN.md` одним коммитом. Конфликт в `theme.css` не правим руками — перегенерируем.

Package-скрипт (добавить при скаффолдинге): `"gen:tokens": "node --experimental-strip-types --no-warnings ../../.agents/skills/frontend-fsd/scripts/gen-tokens.mjs src/shared/config/tokens.ts --css src/app/styles/theme.css --md docs/DESIGN.md"`.

## 1. Характер
От Сбера обязательны только **фирменный зелёный и SB Sans** (ADR-036). Публичная часть гайдлайна Nova — стартовая точка, не закон: полный брендбук закрыт для внешних, поэтому остальное (радиусы, вспомогательные цвета, глубина, движение) — наша современная система.
- Светлые спокойные поверхности (`background`, `card`), много воздуха, крупная типографика SB Sans, мягкие радиусы.
- **Один акцент — зелёный.** Действие = `primary` (кнопка Nova). «Зелёный СБЕР» = `brand` — плоскости, иконки, крупный текст, крупные статусы «готово». Мелкий зелёный текст — только `text-accent-foreground`.
- **Мастер-градиент** (`bg-brand-gradient`, `text-brand-gradient`, `bg-brand-gradient-conic`) — подпись бренда, дозированно: фон входа, кольцо таймера QR, бордер-бим активной карточки, пустые состояния. Не на кнопках, таблицах и тексте длиннее двух слов.
- Нейтральные — только наши серые через семантику (`muted`, `secondary`, `border`). Дефолтная палитра Tailwind (`slate`, `red-500` …) выключена в теме — такие классы просто не сгенерируются.
- Логотип — **заглушка** (`shared/ui/Logo`: знак-галочка в градиенте + слово «Субсидии»), пока не будет официального. Фото — только от человека (см. `frontend-fsd/references/sber-brand.md`).
- Токены общие с «Проходной» (ADR-013): `decision-*`, `text-5xl/6xl`, `z-kiosk`, `motion.kiosk.*` в этом кейсе не используются.

## 2. Токены (сгенерировано)
<!-- tokens:begin -->
<!-- Сгенерировано gen-tokens.mjs из tokens.ts. Правь tokens.ts и запускай `npm run gen:tokens`. -->

#### Семантические цвета

| Токен | Класс | Светлая | Тёмная | Контраст с -foreground (свет / тём.) |
|---|---|---|---|---|
| `background` | `bg-background` / `text-background` | `#F9F9F9` | `#080808` |  |
| `foreground` | `bg-foreground` / `text-foreground` | `#171717` | `#F5F5F5` |  |
| `card` | `bg-card` / `text-card` | `#FFFFFF` | `#171717` | 17.93 / 16.44 |
| `popover` | `bg-popover` / `text-popover` | `#FFFFFF` | `#171717` | 17.93 / 16.44 |
| `primary` | `bg-primary` / `text-primary` | `#148F2B` | `#24B23E` | 4.21 (только крупный) / 7.16 |
| `primary-hover` | `bg-primary-hover` / `text-primary-hover` | `#0D8523` | `#15D13B` |  |
| `brand` | `bg-brand` / `text-brand` | `#21A038` | `#24B23E` | 3.41 (только крупный) / 7.16 |
| `secondary` | `bg-secondary` / `text-secondary` | `#E8E8E8` | `#262626` | 14.63 / 13.88 |
| `muted` | `bg-muted` / `text-muted` | `#F5F5F5` | `#262626` | 4.54 / 7.14 |
| `accent` | `bg-accent` / `text-accent` | `#E0FFE9` | `#0A2B10` | 7.69 / 10.91 |
| `destructive` | `bg-destructive` / `text-destructive` | `#E31227` | `#FF3D51` | 4.79 / 5.76 |
| `warning` | `bg-warning` / `text-warning` | `#B54300` | `#FA6D20` | 5.57 / 6.94 |
| `info` | `bg-info` / `text-info` | `#0C72B6` | `#199AF0` | 5.12 / 6.60 |
| `border` | `bg-border` / `text-border` | `#DCDCDC` | `#363636` |  |
| `input` | `bg-input` / `text-input` | `#DCDCDC` | `#363636` |  |
| `ring` | `bg-ring` / `text-ring` | `#21A038` | `#24B23E` |  |
| `overlay` | `bg-overlay` / `text-overlay` | `rgb(8 8 8 / 0.5)` | `rgb(0 0 0 / 0.7)` |  |
| `decision-allow` | `bg-decision-allow` / `text-decision-allow` | `#21A038` | `#24B23E` | 3.41 (только крупный) / 7.16 |
| `decision-deny` | `bg-decision-deny` / `text-decision-deny` | `#E31227` | `#FF3D51` | 4.79 / 5.76 |
| `decision-manual` | `bg-decision-manual` / `text-decision-manual` | `#D14D00` | `#FA6D20` | 4.40 (только крупный) / 6.94 |
| `decision-error` | `bg-decision-error` / `text-decision-error` | `#4E4E4E` | `#707070` | 8.32 / 4.95 |
| `chart-1` | `bg-chart-1` / `text-chart-1` | `#21A038` | `#24B23E` |  |
| `chart-2` | `bg-chart-2` / `text-chart-2` | `#00ADEE` | `#00ADEE` |  |
| `chart-3` | `bg-chart-3` / `text-chart-3` | `#42E3B4` | `#42E3B4` |  |
| `chart-4` | `bg-chart-4` / `text-chart-4` | `#0087CD` | `#199AF0` |  |
| `chart-5` | `bg-chart-5` / `text-chart-5` | `#A0E720` | `#A0E720` |  |

Пары `x-foreground` — цвет текста/иконок на фоне `x`. Исключения по контрасту (только крупный текст):

- `primary` — Nova: кнопка #148F2B с белым текстом (≈4.2:1). Текст кнопок — semibold 16px+; мелкий зелёный текст — только text-accent-foreground
- `brand` — «Зелёный СБЕР» #21A038 с белым ≈3.4:1 — только плоскости, иконки и текст ≥ 19px semibold; кнопки — primary
- `decision-allow` — экран вердикта киоска: текст ≥ 48px
- `decision-manual` — экран вердикта киоска: текст ≥ 48px

#### Шкалы (только для токенов, не в className)

| Шкала | Значения |
|---|---|
| white | `#FFFFFF` |
| black | `#080808` |
| green | 50 `#E0FFE9`, 100 `#62F582`, 200 `#2AE853`, 300 `#15D13B`, 400 `#24B23E`, 500 `#21A038`, 600 `#148F2B`, 700 `#0D8523`, 800 `#095C18`, 900 `#0A4014`, 950 `#0A2B10` |
| gray | 50 `#F9F9F9`, 100 `#F5F5F5`, 200 `#E8E8E8`, 300 `#DCDCDC`, 400 `#C7C7C7`, 500 `#B2B2B2`, 600 `#858585`, 650 `#7A7A7A`, 700 `#707070`, 800 `#4E4E4E`, 850 `#363636`, 900 `#262626`, 950 `#171717`, 1000 `#080808` |
| orange | 400 `#FA6D20`, 500 `#F55D05`, 600 `#E35502`, 700 `#D14D00`, 800 `#B54300` |
| red | 400 `#FF3D51`, 500 `#F31B31`, 700 `#E31227` |
| blue | 400 `#199AF0`, 500 `#078BE4`, 700 `#067DCC`, 800 `#0C72B6` |
| teal | 400 `#0C9597`, 500 `#0C8688`, 600 `#05996F`, 700 `#0A8A66` |
| purple | 700 `#722BA1` |
| sber | green `#21A038`, sky `#00ADEE`, blue `#0087CD`, arctic `#42E3B4`, spring `#A0E720`, sun `#FAED00` |

#### Типографика (шкала Nova; дефолтные размеры Tailwind выключены)

| Класс | Размер / интерлиньяж | Для чего |
|---|---|---|
| `text-xs` | 13 / 16 px | подписи, бейджи, легенды графиков |
| `text-sm` | 15 / 20 px | основной текст админки, ячейки таблиц, инпуты |
| `text-base` | 16 / 24 px | основной текст телефона, абзацы |
| `text-lg` | 19 / 24 px | заголовок карточки, h4 |
| `text-xl` | 22 / 28 px | заголовок секции, h3 |
| `text-2xl` | 28 / 36 px | заголовок страницы, h2 |
| `text-3xl` | 34 / 40 px | h1 админки, крупные KPI |
| `text-4xl` | 37 / 44 px | h1 лендинга/телефона |
| `text-5xl` | 48 / 56 px | вердикт киоска: причина (наше, не Nova) |
| `text-6xl` | 64 / 72 px | вердикт киоска: ПРОХОДИТЕ / ОТКАЗ (наше, не Nova) |

Шрифты: `font-sans` — SB Sans Text (весь UI), `font-display` — SB Sans Display (h1–h3, KPI, вердикт). Начертания: `font-regular` 400, `font-medium` 500, `font-semibold` 600, `font-bold` 700.

#### Отступы, контролы, радиусы, тени, слои

- Сетка 4 px. Для `p-* m-* gap-* space-* inset-*` разрешены шаги: `0` (0 px), `0.5` (2 px), `1` (4 px), `2` (8 px), `3` (12 px), `4` (16 px), `5` (20 px), `6` (24 px), `8` (32 px), `10` (40 px), `12` (48 px), `16` (64 px).
- Высота контролов: `h-control-xs` 32, `h-control-sm` 40, `h-control-md` 48, `h-control-lg` 56, `h-control-xl` 64 px.
- Радиусы: `rounded-xs` 6, `rounded-sm` 10, `rounded-md` 14, `rounded-lg` 20, `rounded-xl` 28, `rounded-2xl` 36 px, `rounded-full`.
- Тени: `shadow-xs`, `shadow-sm`, `shadow-card`, `shadow-pop` (свои значения в тёмной теме).
- Safe-area телефона: `pt-safe`, `pb-safe`.
- Слои: `z-base` 0, `z-sticky` 10, `z-dropdown` 40, `z-overlay` 50, `z-modal` 60, `z-toast` 70, `z-kiosk` 80.

#### Движение (motion/react через `@/shared/config/motion`; CSS-переходы — классами)

| Токен | Значение | CSS-класс |
|---|---|---|
| duration.instant | 0.1 с | `duration-instant` |
| duration.fast | 0.2 с | `duration-fast` |
| duration.base | 0.3 с | `duration-base` |
| duration.slow | 0.4 с | `duration-slow` |
| duration.hero | 0.6 с | `duration-hero` |
| ease.out | `cubic-bezier(0.22, 1, 0.36, 1)` | `ease-out` |
| ease.in | `cubic-bezier(0.4, 0, 1, 1)` | `ease-in` |
| ease.inOut | `cubic-bezier(0.65, 0, 0.35, 1)` | `ease-in-out` |
| ease.emphasized | `cubic-bezier(0.2, 0, 0, 1)` | `ease-emphasized` |
| spring.press | stiffness 600, damping 30, mass 0.6 | — |
| spring.snappy | stiffness 420, damping 34 | — |
| spring.soft | stiffness 240, damping 28 | — |
| spring.sheet | stiffness 320, damping 36, mass 0.9 | — |
| spring.bouncy | bounce 0.35, visualDuration 0.45 | — |
| swipe | offset 80, velocity 500, confirmRatio 0.85 | — |
| toast | durationMs 4000, errorDurationMs 6000 | — |
| kiosk | resultHoldMs 4000, challengeTimeoutMs 10000 | — |
<!-- tokens:end -->

## 3. Типографика — как применять
- `font-sans` (SB Sans Text) — весь UI; `font-display` (SB Sans Display) — h1–h3, KPI-цифры, итоговые статусы. Цифры табличные (включено в `body`).
- На экране не больше трёх размеров и двух начертаний. Основной текст: рабочее место `text-sm`, формы `text-base`.
- Иерархия: страница `text-2xl font-semibold` · секция `text-xl font-semibold` · карточка `text-lg font-medium` · подпись `text-xs text-muted-foreground`.
- Пакет: статус пакета `text-2xl font-display font-semibold`, суммы `text-sm font-medium tabular-nums`, «ожидалось/фактически» — `text-xs text-muted-foreground` + значение `text-sm`.
- Тексты на «вы», коротко: что случилось + что делать («Снимок размыт. Держите телефон неподвижно и снимите ещё раз»).

## 4. Отступы, сетка, раскладка
- Сетка 4 px; разрешённые шаги — в разделе 2 (`spacing-scale` в `fsd-check`). Внутри контрола `gap-2`, внутри карточки `p-4`/`p-6` и `gap-3`/`gap-4`, между карточками `gap-4`/`gap-6`, между секциями `gap-8`/`gap-12`.
- Поля страницы: `px-6 lg:px-8`.
- Сайдбар `w-64`, страница пакета — две панели на всю ширину, таблицы во всю ширину; ниже 1280 px — вкладки «Документы / Оригинал / Протокол».
- Тач-цели: `h-control-sm`/`h-control-md` (desktop).

## 5. Компоненты (спецификация)
Поведение и доступность — от примитива (Radix/shadcn, cmdk), вид и движение — наши (`frontend-fsd/references/custom-controls.md`, каталог — `component-catalog.md`). Всё лежит в `shared/ui/<Comp>/`, варианты — через `cva`.

| Компонент | Вид | Варианты / размеры | Состояния и движение |
|---|---|---|---|
| Button | `rounded-md font-semibold px-5`, текст `text-base` (md) | `primary` (bg-primary, hover:bg-primary-hover), `secondary`, `ghost`, `outline`, `destructive`, `link`; `h-control-sm/md/lg`, крупное действие телефона `lg` | `press` (scale hover/tap), `loading` → спиннер на motion + блок повторного клика, `focus-visible:ring-2 ring-ring`, disabled `opacity-50` |
| IconButton | квадрат `size-control-*`, `rounded-md` | как Button | то же; обязательный `aria-label` |
| Input / Textarea | `h-control-md rounded-md border-input bg-card px-4 text-sm` | с иконкой, с маской (телефон, код) | focus `ring-2 ring-ring`, ошибка `border-destructive` + текст `text-xs text-destructive` |
| Select / Combobox / DatePicker | кастомные (Radix Popover + cmdk / react-day-picker), никаких нативных | single/multi, с поиском (`options?q=` бэка) | `scaleIn` поповера, `spring.snappy` индикатора |
| Checkbox / Switch / Radio / Segmented | `rounded-xs` / pill / круг / `rounded-md` трек | sm/md | `spring.press` для ползунка и галочки |
| Card | `bg-card rounded-lg shadow-card p-4 md:p-6` | `interactive` (hover `shadow-sm`→`card`), `selected` (бордер-бим градиентом) | `inView` при прокрутке, `layout` при перестройке |
| Badge / Chip | `rounded-sm px-2 text-xs font-medium` | `neutral`, `success`(accent), `warning`, `destructive`, `info`; статус документа — по `stageView` | без анимации, кроме появления |
| Dialog / Sheet | `bg-popover rounded-xl shadow-pop`, overlay `bg-overlay` | desktop — центр, телефон — bottom sheet `rounded-t-xl` | `scaleIn` / `spring.sheet`, свайп вниз `swipe.*` |
| Tabs | подчёркивание или сегмент | — | индикатор `layoutId` + `spring.snappy` |
| Table | строка `h-control-md`, `text-sm`, шапка sticky `bg-muted text-xs`, числа справа | сортировка, фильтры в URL, пагинация курсором | skeleton строк, `layout` при сортировке |
| Toast | `rounded-lg shadow-pop` | success / error / info | длительность `motion.toast.*`, `fadeUp` |
| Skeleton | `bg-muted rounded-md` | строки/карточки | мерцание на motion (`opacity`), не `animate-pulse` |
| Empty / Error / Offline | иллюстрация или иконка `size-12 text-muted-foreground`, заголовок `text-lg`, текст, кнопка | empty, error («Повторить»), offline-баннер `bg-warning` | `fadeUp` |
| KPI-плитка | `Card` + `font-display text-3xl` + подпись + «как посчитано» (tooltip) | с дельтой, с «неполные данные» (`text-warning`) | `NumberTicker` на motion |
| Chart | recharts/evilcharts, цвета `cssVar('chart-1…5')` | line, bar, heatmap (B-08) | появление линии, без бесконечных анимаций |
| **FileList** (пакет) | `Table`: строка файла (иконка формата, имя только для показа, версия, стадия) → дочерние строки документов (вид, страницы, уверенность — полоска + число) | норм / сомнительный (`bg-warning/10`, сверху) / отклонён (`text-destructive` + подсказка) / UNREADABLE | раскрытие сегментов — `layout`, смена стадии — crossfade `tween.fast`, текущая стадия — пульс `opacity` |
| **DocumentViewer** (пакет) | `bg-muted rounded-lg`, страница на canvas, панель зум/поворот/страницы `IconButton`; bbox — `ring-2 ring-ring` + `bg-primary/10` | PDF / изображение, одна/много страниц | зум/пан — `react-zoom-pan-pinch`, фокус bbox — `spring.soft` |
| **FieldsPanel** (пакет) | список: метка `text-xs`, значение `text-sm font-medium tabular-nums`, уверенность, проверки — `Badge` | обычное / «перепроверить» / исправлено (`Badge info`) | inline-редактирование, `Enter`/`Esc`, сохранено — `spring.bouncy` |
| **ProtocolPanel** (протокол) | `Card` с группами FAILED (`destructive`) / WARNING (`warning`) / DATA_MISSING (`info`) / PASSED (`success`, свёрнуто); у правила: текст, «ожидалось / фактически» `tabular-nums`, ссылки на документы | версия протокола, «устарел — идёт перепроверка» | обновление — `fadeUp` новых строк, счётчики `NumberTicker` |
| **CompletenessCard** (протокол) | кольцо `bg-brand-gradient-conic` + список пунктов чек-листа (есть / нет / условие не применимо) | полный (`brand`) / не хватает (`warning`) | рост кольца `tween.slow` |

## 6. Зоны
| Зона | Тема | Плотность | Особенности |
|---|---|---|---|
| `/specialist` | светлая + переключатель | `text-sm`, таблицы | сайдбар, фильтры в URL, страница пакета — две панели (документы и оригинал слева, поля и протокол справа, ≥ 1280 px), горячие клавиши, «как посчитано» у статистики, суммы `tabular-nums` по правому краю |

## 7. Иконки и изображения
- Основной набор — **Plasma Icons** (открытые иконки Сбера, MIT): SVG из `@salutejs/plasma-icons` копируем в `shared/ui/Icon` (без styled-components), с атрибуцией. Чего нет — `lucide-react` со `strokeWidth={1.75}`; анимированные — `@lucide-animated`. Размеры: `size-4` в тексте, `size-5` в кнопках, `size-6` в навигации.
- Картинки — только по запросу к человеку (реальные или генерация), webp ≤ 200 КБ, `loading="lazy"`; документы и ПДн никогда не в git/`public`.

## 8. Движение — как применять
Значения — раздел 2, правила и рецепты — `frontend-fsd/references/motion.md`. UI — motion; 3D, шейдеры и фоны — R3F/ogl (вход, пустые состояния, экран «комплект собран»); сложные таймлайны — GSAP; один движок на компонент (ADR-036). Кратко: UI-переходы `duration.fast`/`base`, крупные — `slow`, `hero` только для лендинга и слайдов. Вход — `ease.out`, уход короче и `ease.in`. Пружины — для нажатий, поповеров, шторок. Анимируем `transform`/`opacity`, уважаем `prefers-reduced-motion` (`MotionConfig reducedMotion="user"`). Ничего не крутится бесконечно, кроме индикатора текущей стадии.

## 9. Доступность
Контраст ≥ 4.5:1 проверяет генератор; фокус всегда виден (`ring-ring`); вся навигация с клавиатуры; `aria-label` у иконок-кнопок; цвет никогда не единственный носитель смысла (статус = цвет + иконка + текст).

## 10. Чек-лист дизайн-ревью экрана
- [ ] Только токены: `fsd-check` чистый, нет hex/arbitrary/литералов анимаций.
- [ ] Шкала текста и отступов по разделам 3–4; тач-цели по зоне.
- [ ] Состояния: loading / empty / error / offline / disabled / focus.
- [ ] Светлая и тёмная тема, 390×844 и 1440×900.
- [ ] Кастомные контролы, не нативные; движение по пресетам; reduced-motion.
- [ ] Тексты из i18n, на «вы», ошибка = что случилось + что делать.
