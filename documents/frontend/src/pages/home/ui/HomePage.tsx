import { Link } from "react-router";
import { motion } from "motion/react";
import { ArrowRight, Smartphone, MonitorCheck, ScanText, ShieldCheck, Zap } from "lucide-react";
import { HeaderBar, Logo, PreferencesButton, Button } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { fadeUp, stagger, lift } from "@/shared/config/motion";

const ZONES = [
  { to: routes.apply, icon: Smartphone, title: "Кабинет соискателя", text: "Анкета, список нужных документов, загрузка или фото с телефона, статус разбора в реальном времени", device: "Телефон, без регистрации", cta: "Собрать документы" },
  { to: routes.operator, icon: MonitorCheck, title: "Рабочее место оператора", text: "Очередь, оригинал рядом с распознанными полями, подсветка сомнительного, решение и отчёты", device: "Компьютер, вход через SSO", cta: "Войти" },
];
const FEATURES = [
  { icon: ScanText, text: "Вид документа определяется по содержимому, а не по пункту загрузки" },
  { icon: Zap, text: "Плохой снимок — сразу с подсказкой, что исправить" },
  { icon: ShieldCheck, text: "Модель работает внутри контура, ПДн наружу не уходят" },
];

/** Главная: две зоны одного приложения. */
export const HomePage = () => (
  <div className="relative isolate min-h-svh text-foreground">
    <HeaderBar>
      <Logo sub="приём документов" className="px-1.5" />
      <div className="ml-auto"><PreferencesButton /></div>
    </HeaderBar>
    <main className="mx-auto w-full max-w-5xl px-4 pb-16 pt-10 sm:px-6 sm:pt-16">
      <motion.div variants={stagger()} initial="hidden" animate="show" className="text-center">
        <motion.p variants={fadeUp} className="text-sm font-medium text-brand">Оформление на работу без бумажной волокиты</motion.p>
        <motion.h1 variants={fadeUp} className="mx-auto mt-3 max-w-3xl text-balance font-display text-4xl font-semibold tracking-hero sm:text-5xl">Документы соискателя — разобраны и сверены за минуты</motion.h1>
        <motion.ul variants={fadeUp} className="mx-auto mt-6 flex max-w-3xl flex-wrap justify-center gap-2">
          {FEATURES.map(({ icon: Icon, text }) => <li key={text} className="flex items-center gap-2 rounded-full bg-card/80 px-3 py-1.5 text-sm"><Icon className="size-4 text-brand" />{text}</li>)}
        </motion.ul>
      </motion.div>
      <motion.div variants={stagger()} initial="hidden" animate="show" className="mt-10 grid gap-4 md:grid-cols-2">
        {ZONES.map(({ to, icon: Icon, title, text, device, cta }) => (
          <motion.div key={to} variants={fadeUp} {...lift}>
            <Link to={to} className="flex h-full flex-col rounded-2xl bg-card p-6 shadow-card outline-none transition-shadow duration-base hover:shadow-pop focus-visible:ring-2 focus-visible:ring-ring sm:p-8">
              <span className="grid size-12 place-items-center rounded-lg bg-brand-deep text-white"><Icon className="size-6" /></span>
              <h2 className="mt-5 font-display text-2xl font-semibold tracking-display">{title}</h2>
              <p className="mt-2 flex-1 text-muted-foreground">{text}</p>
              <p className="mt-4 text-xs text-subtle-foreground">{device}</p>
              <Button className="mt-4 self-start" tabIndex={-1}>{cta}<ArrowRight /></Button>
            </Link>
          </motion.div>
        ))}
      </motion.div>
    </main>
  </div>
);
