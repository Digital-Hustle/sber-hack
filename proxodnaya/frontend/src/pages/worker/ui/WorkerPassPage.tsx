import { useMemo } from "react";
import { Link, useOutletContext } from "react-router";
import { motion } from "motion/react";
import { ChevronRight, ShieldCheck, CalendarClock, ScanFace } from "lucide-react";
import { useDb, presenceNow, shiftFor, workedMs, buildIntervals, workerSites, siteOfZone } from "@/shared/api";
import { LogoMark, PageHeader, Progress } from "@/shared/ui";
import { atTime, durationRu, hhmm, todayKey, cn, plural } from "@/shared/lib";
import { useNow, useOnline } from "@/shared/hooks";
import { routes } from "@/shared/const/router";
import { PassQr } from "@/features/show-pass-qr";
import type { WorkerCtx } from "@/widgets/worker-shell";
import { fadeUp, stagger, spring, tween } from "@/shared/config/motion";
import { useWorkerCard } from "../lib/card";

const todayRu = () => new Date().toLocaleDateString("ru-RU", { weekday: "long", day: "numeric", month: "long" });
const greet = (h = new Date().getHours()) => (h < 5 ? "Доброй ночи" : h < 12 ? "Доброе утро" : h < 18 ? "Добрый день" : "Добрый вечер");

/** Плитка цифры дня — как на лендинге: крупная цифра Display и подпись под ней. */
const Tile = ({ value, label, className, children }: { value: React.ReactNode; label: React.ReactNode; className?: string; children?: React.ReactNode }) => (
  <motion.div variants={fadeUp} className={cn("flex min-w-0 flex-col gap-1 rounded-xl bg-card px-5 py-4 shadow-card", className)}>
    <dd className="truncate font-display text-2xl font-semibold tabular-nums tracking-hero sm:text-3xl">{value}</dd>
    <dt className="truncate text-sm text-muted-foreground">{label}</dt>
    {children}
  </motion.div>
);

/**
 * Пропуск (v4, ADR-046) — по мотивам лендинга: фирменная карточка «Пропуск на объект» с белой плашкой кода,
 * под ней плитки дня крупными цифрами и полоса смены. Имя — в приветствии, данные человека — в профиле.
 */
export const WorkerPassPage = () => {
  const { key } = useOutletContext<WorkerCtx>();
  const db = useDb();
  const now = useNow(30000);
  const online = useOnline();
  const w = useWorkerCard(db.workers.find((x) => x.id === key.workerId), key.workerId);
  const pres = useMemo(() => presenceNow(db).find((p) => p.workerId === key.workerId), [db, key.workerId]);
  const sh = shiftFor(db, key.workerId);
  const worked = useMemo(() => workedMs(db, key.workerId, todayKey(), buildIntervals(db), now), [db, key.workerId, now]);
  const sites = useMemo(() => workerSites(db, key.workerId), [db, key.workerId]);
  if (!w) return <div className="py-10 text-center text-muted-foreground">Сотрудник не найден на этом устройстве</div>;

  const firstName = w.fullName.split(" ")[1] ?? w.fullName;
  const face = db.workers.find((x) => x.id === key.workerId)?.face;
  const here = pres ? siteOfZone(db, pres.zoneId) : undefined;
  const zoneName = pres ? db.zones.find((z) => z.id === pres.zoneId)?.name : undefined;
  const siteLabel = here?.name ?? (sites.length === 1 ? sites[0].site.name : `${sites.length} ${plural(sites.length, "объект", "объекта", "объектов")}`);

  const start = sh ? atTime(todayKey(), sh.start) : 0, end = sh ? atTime(todayKey(), sh.end) : 0;
  const shiftPart = sh ? (now - start) / (end - start) : 0;
  const shiftNote = !sh ? "Смены сегодня нет" : now < start ? `Начнётся через ${durationRu(start - now)}` : now < end ? `До конца ${durationRu(end - now)}` : "Смена закончилась";

  return (
    <div>
      <PageHeader kicker={<span className="first-letter:uppercase">{todayRu()}</span>} title={`${greet()}, ${firstName}`}
        sub={!online ? "Нет сети, но пропуск работает: код подписывается на телефоне" : pres ? "На выходе покажите код ещё раз" : "Покажите код камере терминала на проходной"} />

      {/* Карточка пропуска — как на лендинге: фирменная заливка с бликом, знак, объект и код на белой плашке. */}
      <motion.article initial={{ opacity: 0, y: 40 }} animate={{ opacity: 1, y: 0 }} transition={{ ...spring.soft, opacity: tween.base }}
        className="relative isolate overflow-hidden rounded-2xl bg-brand-deep p-4 text-white shadow-pop sm:p-5">
        <span aria-hidden className="absolute inset-0 -z-10 bg-sheen" />
        <header className="flex items-center gap-3">
          <LogoMark className="size-10 shadow-none" />
          <Link to={routes.workerProfile} className="group min-w-0 flex-1 rounded-sm leading-tight outline-none focus-visible:ring-2 focus-visible:ring-white">
            <div className="truncate font-semibold">Пропуск на объект</div>
            <div className="flex items-center gap-0.5 text-sm text-white/80 transition-colors duration-fast group-hover:text-white"><span className="truncate">{siteLabel}</span><ChevronRight className="size-4 shrink-0" /></div>
          </Link>
          <span className={cn("inline-flex max-w-2/5 shrink-0 items-center rounded-2xs px-2 py-1 text-xs font-medium", pres ? "bg-white text-brand" : "bg-white/15 text-white/85")}>
            <span className="truncate">{pres ? (zoneName ?? "На объекте") : "Вне объекта"}</span>
          </span>
        </header>
        <div className="mt-4"><PassQr tone="brand" /></div>
      </motion.article>

      {/* ADR-046: эталона лица нет или он на проверке — подсказываем, что сделать. */}
      {face && face.status !== "ACTIVE" && (
        <motion.div variants={fadeUp} initial="hidden" animate="show" className="mt-3 sm:mt-4">
          <Link to={routes.workerFace} className="group flex items-center gap-4 rounded-xl bg-card p-4 shadow-card outline-none transition-shadow duration-base hover:shadow-float focus-visible:ring-2 focus-visible:ring-ring">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-accent text-accent-foreground"><ScanFace className="size-5" /></span>
            <span className="min-w-0 flex-1">
              <span className="block font-medium">{face.status === "PENDING" ? "Лицо на проверке" : face.status === "REJECTED" ? "Снимок не подошёл" : "Добавьте лицо для прохода"}</span>
              <span className="block text-pretty text-sm text-muted-foreground">{face.status === "PENDING" ? "Пока не подтвердят, на проверке лица пропустит охранник" : face.status === "REJECTED" ? (face.comment ?? "Переснимите при хорошем свете") : "30 секунд: поверните голову перед камерой телефона"}</span>
            </span>
            <ChevronRight className="size-5 shrink-0 text-subtle-foreground transition-transform duration-fast group-hover:translate-x-0.5" />
          </Link>
        </motion.div>
      )}

      <motion.dl variants={stagger(0.06, 0.12)} initial="hidden" animate="show" className="mt-3 grid grid-cols-2 gap-3 sm:mt-4 sm:gap-4">
        <Tile value={durationRu(worked)} label="сегодня" />
        <Tile value={pres ? hhmm(pres.since) : "—"} label={pres ? "время входа" : "вход не отмечен"} />
        <Tile className="col-span-2" value={sh ? `${sh.start}–${sh.end}` : "Выходной"} label={shiftNote}>
          {sh ? <Progress value={shiftPart} className="mt-2" />
            : <Link to={routes.workerShifts} className="mt-1 inline-flex items-center gap-1 self-start text-sm font-medium text-brand underline-offset-4 hover:underline"><CalendarClock className="size-4" />Все смены</Link>}
        </Tile>
      </motion.dl>

      <motion.div variants={fadeUp} initial="hidden" animate="show" className="mt-3 flex gap-4 rounded-xl bg-muted p-4 sm:mt-4">
        <span className="relative flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-md bg-brand-deep text-white">
          <span aria-hidden className="absolute inset-0 bg-sheen" /><ShieldCheck className="relative size-5" />
        </span>
        <p className="min-w-0 self-center text-pretty text-sm text-foreground/80">Один код на все ваши объекты. Он одноразовый и меняется каждые 30 секунд, поэтому скриншот не подойдёт.</p>
      </motion.div>
    </div>
  );
};
