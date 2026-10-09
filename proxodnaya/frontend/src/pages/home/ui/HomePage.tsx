import { Link } from "react-router";
import { motion } from "motion/react";
import { ArrowRight, ScanLine, ChevronRight, ScrollText, BarChart3, Sparkles, ShieldCheck } from "lucide-react";
import { Logo, LogoMark, Button, AnimatedNumber, HeaderBar, PreferencesButton, StyledQr, BrandConicFill } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { routes, absoluteUrl } from "@/shared/const/router";
import { fadeUp, stagger, liftArea, liftItem, liftZoom, spring, inView, tween, duration } from "@/shared/config/motion";

const art = (name: string) => `${import.meta.env.BASE_URL}art/${name}.webp`;

const ZONES = [
  { to: routes.kiosk, img: art("kiosk"), title: "Киоск на проходной", text: "Проверка QR-пропуска и лица, решение о допуске за 2–3 секунды", device: "Ноутбук или планшет с камерой" },
  { to: routes.worker, img: art("phone"), title: "Пропуск сотрудника", text: "Динамический QR-код обновляется каждые 30 секунд и работает офлайн", device: "Смартфон" },
  { to: routes.admin, img: art("admin"), title: "Кабинет руководителя", text: "Присутствие на объекте, журнал проходов, смены, аналитика и помощник", device: "Компьютер или планшет" },
];

const STORIES = [
  { to: routes.kiosk, label: "Киоск", img: art("kiosk") },
  { to: routes.worker, label: "Пропуск", img: art("phone") },
  { to: routes.admin, label: "Обстановка", img: art("admin") },
  { to: routes.adminJournal, label: "Журнал проходов", icon: ScrollText, tone: "bg-brand-deep" },
  { to: routes.adminAnalytics, label: "Аналитика", icon: BarChart3, tone: "bg-brand" },
  { to: routes.adminAssistant, label: "Помощник", icon: Sparkles, tone: "conic" },
];

/** Герой как у sberbank.ru: две карточки внахлёст с наклоном — подсказка и живой пропуск. */
const HeroCards = () => (
  <div className="relative mx-auto mt-10 h-80 w-full max-w-sm sm:mt-14 sm:h-96 sm:max-w-xl">
    <motion.div initial={{ opacity: 0, y: 40, rotate: 0 }} animate={{ opacity: 1, y: 0, rotate: 5 }} transition={{ ...spring.soft, opacity: tween.base }}
      whileHover={{ rotate: 2, y: -6, transition: spring.hover }}
      className="absolute right-2 top-0 w-56 overflow-hidden rounded-2xl bg-brand-deep p-4 text-white shadow-pop sm:right-6 sm:w-72 sm:p-5">
      <div aria-hidden className="absolute inset-0 bg-sheen" />
      <div className="relative flex items-center gap-2.5">
        <LogoMark className="size-8 shadow-none" />
        <div className="min-w-0 text-xs leading-tight text-white/80"><div className="font-semibold text-white">Пропуск на объект</div>ЖК «Северный»</div>
      </div>
      <div className="relative mt-4 rounded-xl bg-white p-3"><StyledQr value="v1.example.proxodnaya" label="Пример QR-пропуска" /></div>
      <div className="relative mt-3 flex items-center justify-between text-xs text-white/80"><span>Новый код через 24 с</span><ShieldCheck className="size-4" /></div>
    </motion.div>
    <motion.div initial={{ opacity: 0, y: 40, rotate: 0 }} animate={{ opacity: 1, y: 0, rotate: -6 }} transition={{ ...spring.soft, opacity: tween.base, delay: duration.instant }}
      whileHover={{ rotate: -3, y: -6, transition: spring.hover }}
      className="absolute bottom-2 left-3 flex w-44 flex-col overflow-hidden rounded-2xl bg-card shadow-pop sm:bottom-0 sm:left-4 sm:w-60">
      <div className="flex-1 bg-linear-to-br from-sber-lime/60 via-sber-mint/30 to-transparent p-5 pb-10">
        <p className="font-display text-lg font-semibold leading-tight tracking-display text-foreground sm:text-xl">Проход по QR-коду и сверке лица</p>
      </div>
      <Link to={routes.kiosk} className="flex items-center gap-1 bg-card/80 px-5 py-3.5 text-sm text-muted-foreground transition-colors duration-fast hover:text-foreground">Открыть киоск<ChevronRight className="size-4" /></Link>
    </motion.div>
  </div>
);

export const HomePage = () => {
  // Главная открыта без входа — здесь только свойства продукта, живые цифры — в кабинете.
  const stats = [
    { value: 30, label: "секунд действует QR" },
    { value: 1, label: "QR на все объекты" },
    { value: 12, label: "часов работы без связи" },
    { value: 0, label: "паролей у персонала" },
  ];

  return (
    <div className="relative isolate min-h-svh overflow-x-clip">
      <HeaderBar inner="max-w-6xl">
        <Logo className="px-1.5" />
        <nav aria-label="Разделы" className="ml-auto hidden items-center gap-1 md:flex">
          {[{ to: routes.kiosk, label: "Киоск" }, { to: routes.worker, label: "Пропуск" }, { to: routes.admin, label: "Кабинет" }].map(({ to, label }) => (
            <Link key={to} to={to} className="flex h-10 items-center rounded-sm px-4 text-sm font-medium text-muted-foreground outline-none transition-colors duration-fast hover:bg-surface hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring">{label}</Link>
          ))}
        </nav>
        <div className="ml-auto flex shrink-0 items-center gap-1.5 md:ml-0">
          <PreferencesButton />
          <Link to={routes.kiosk} tabIndex={-1} className="hidden sm:block"><Button variant="brand" className="h-12 rounded-md px-5"><ScanLine />Открыть киоск</Button></Link>
        </div>
      </HeaderBar>

      <main className="relative mx-auto max-w-6xl px-4 pb-10 sm:px-6">
        <motion.section variants={stagger(0.07)} initial="hidden" animate="show" className="flex flex-col items-center pt-12 text-center sm:pt-20">
          <motion.p variants={fadeUp} className="text-sm font-medium text-brand">Пропускная система для объектов</motion.p>
          <motion.h1 variants={fadeUp} className="mt-6 max-w-5xl text-balance font-display text-4xl font-semibold leading-none tracking-hero sm:text-5xl lg:text-6xl">
            Контроль доступа на строительный объект
          </motion.h1>
          <motion.p variants={fadeUp} className="mt-5 max-w-2xl text-pretty text-base text-foreground/70 sm:text-lg">
            Сотрудник предъявляет динамический QR-код, киоск сверяет лицо и сам определяет вход или выход. Руководитель видит обстановку на объекте в реальном времени.
          </motion.p>
          <motion.div variants={fadeUp} className="mt-8 flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:gap-3">
            <Link to={routes.kiosk} tabIndex={-1}><Button variant="brand" size="lg" block className="h-14 rounded-lg px-8"><ScanLine />Открыть киоск</Button></Link>
            <Link to={routes.admin} tabIndex={-1}><Button size="lg" variant="secondary" block className="h-14 rounded-lg bg-card px-8 shadow-card hover:bg-muted">Кабинет руководителя<ArrowRight /></Button></Link>
          </motion.div>
        </motion.section>

        <HeroCards />

        {/* «Сторис» как на sberbank.ru: плитки-суперэллипсы с иллюстрациями */}
        <motion.nav variants={stagger(0.05, 0.3)} initial="hidden" animate="show" aria-label="Быстрый доступ"
          className="scrollbar-none relative z-raised -mx-4 mt-8 flex gap-4 overflow-x-auto px-4 pb-4 pt-4 sm:mx-0 sm:mt-12 sm:justify-center sm:gap-6 sm:px-2">
          {STORIES.map(({ to, label, img, icon: Icon, tone }) => (
            <motion.div key={to} variants={fadeUp} className="w-20 shrink-0">
              <motion.div {...liftArea} className="group">
                <Link to={to} className="flex flex-col gap-2 text-left outline-none">
                  <motion.span variants={liftItem} className={cn("relative flex size-20 items-center justify-center overflow-hidden rounded-xl shadow-card ring-2 ring-white/70 transition-shadow duration-base group-hover:shadow-float group-focus-visible:ring-ring dark:ring-white/10", tone !== "conic" && tone)}>
                    {img ? <img src={img} alt="" className="size-full object-cover" loading="lazy" /> : Icon && (<>
                      {tone === "conic" ? <BrandConicFill /> : <span aria-hidden className="absolute inset-0 bg-sheen" />}<Icon className="relative size-8 text-white drop-shadow-sm" />
                    </>)}
                  </motion.span>
                  <span className="text-sm font-semibold leading-tight">{label}</span>
                </Link>
              </motion.div>
            </motion.div>
          ))}
        </motion.nav>

        <motion.section {...inView} className="mt-12 grid grid-cols-2 gap-3 sm:mt-16 sm:gap-4 lg:grid-cols-4">
          {stats.map((s, i) => (
            <div key={s.label} className={cn("relative flex flex-col gap-1 overflow-hidden rounded-xl px-5 py-5 shadow-card sm:px-6 sm:py-6", i === 0 ? "bg-brand-deep text-white" : "bg-card")}>
              {i === 0 && <span aria-hidden className="absolute inset-0 bg-sheen" />}
              <dd className="relative font-display text-4xl font-semibold tabular-nums tracking-hero sm:text-5xl"><AnimatedNumber value={s.value} /></dd>
              <dt className={cn("relative text-sm", i === 0 ? "text-white/80" : "text-muted-foreground")}>{s.label}</dt>
            </div>
          ))}
        </motion.section>

        <section className="mt-16 sm:mt-24">
          <motion.h2 {...inView} className="mb-6 text-balance text-center font-display text-3xl font-semibold tracking-hero sm:mb-10 sm:text-5xl">Три компонента системы</motion.h2>
          <motion.div variants={stagger(0.08)} initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.15 }} className="grid gap-3 sm:gap-4 md:grid-cols-3">
            {ZONES.map(({ to, img, title, text, device }) => (
              <motion.div key={to} variants={fadeUp}>
                <motion.div {...liftArea} className="group h-full">
                  <motion.div variants={liftItem} className="h-full rounded-2xl shadow-card transition-shadow duration-slow group-hover:shadow-float">
                  <Link to={to} className="flex h-full flex-col gap-4 rounded-2xl bg-card p-2 pb-4 outline-none focus-visible:ring-2 focus-visible:ring-ring">
                    <span className="block aspect-4/3 overflow-hidden rounded-xl"><motion.img variants={liftZoom} src={img} alt="" loading="lazy" className="size-full object-cover" /></span>
                    <div className="flex flex-1 flex-col gap-1.5 px-3">
                      <h3 className="font-display text-xl font-semibold tracking-display">{title}</h3>
                      <p className="text-pretty text-sm text-muted-foreground">{text}</p>
                    </div>
                    <div className="flex items-center justify-between gap-2 px-3 text-sm text-muted-foreground">
                      <span className="truncate">{device}</span>
                      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface text-foreground transition-colors duration-fast group-hover:bg-primary group-hover:text-primary-foreground"><ArrowRight className="size-4" /></span>
                    </div>
                  </Link>
                  </motion.div>
                </motion.div>
              </motion.div>
            ))}
          </motion.div>
        </section>

        <motion.section {...inView} className="mt-16 grid gap-8 rounded-3xl bg-card/85 p-5 shadow-card backdrop-blur-xl sm:mt-24 sm:p-10 md:grid-cols-5 md:gap-12">
          <div className="flex flex-col items-center gap-4 md:col-span-2">
            <div className="w-full max-w-60 rounded-2xl bg-white p-4 shadow-float ring-1 ring-border"><StyledQr value={absoluteUrl(routes.worker)} label="QR со ссылкой на пропуск" /></div>
            <p className="max-w-60 text-center text-sm text-muted-foreground">Отсканируйте камерой смартфона, чтобы открыть пропуск</p>
          </div>
          <div className="min-w-0 md:col-span-3">
            <h2 className="text-balance font-display text-3xl font-semibold tracking-hero sm:text-4xl">Как начать</h2>
            <ol className="mt-6 flex flex-col gap-3">
              {[
                <>Администратор входит в кабинет по коду из письма, заводит объект, зоны, проходные и сотрудников.</>,
                <>Сотрудник активирует пропуск на телефоне по коду приглашения из кабинета — ключ остаётся на устройстве.</>,
                <>Инженер подключает терминал у турникета кодом сопряжения; дальше система сама определяет вход и выход.</>,
              ].map((t, i) => (
                <li key={i} className="flex gap-4 rounded-xl bg-muted p-4">
                  <span className="relative flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-md bg-brand-deep font-display text-lg font-semibold tabular-nums text-white">
                    <span aria-hidden className="absolute inset-0 bg-sheen" /><span className="relative">{i + 1}</span>
                  </span>
                  <span className="min-w-0 self-center text-pretty text-base text-foreground/80">{t}</span>
                </li>
              ))}
            </ol>
          </div>
        </motion.section>
      </main>

      {/* Подвал как у Сбера: белая панель со скруглённым верхом */}
      <footer className="mt-10 rounded-t-3xl bg-card px-4 pb-safe shadow-card sm:px-6">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 py-8 text-sm text-muted-foreground sm:flex-row">
          <Logo sub="Хакатон «СберБизнесВайб» · 2026" />
          <span>Демо-данные хранятся локально в браузере</span>
        </div>
      </footer>
    </div>
  );
};
