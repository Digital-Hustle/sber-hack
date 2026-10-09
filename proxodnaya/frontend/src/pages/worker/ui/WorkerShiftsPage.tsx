import { useMemo, useState } from "react";
import { useOutletContext } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { CalendarDays, ChevronLeft, ChevronRight, LogIn, LogOut, Timer } from "lucide-react";
import { api, useDb, buildIntervals, workedMs, dayPasses, siteOfCheckpoint } from "@/shared/api";
import { AutoHeight, Button, Card, CardHeader, CardTitle, EmptyState, PageHeader, Progress, Status, type Tone } from "@/shared/ui";
import { atTime, cn, durationRu, hhmm, plural, todayKey } from "@/shared/lib";
import { distance, fadeUp, popIn, press, spring, stagger, tween } from "@/shared/config/motion";
import type { WorkerCtx } from "@/widgets/worker-shell";

const WEEK = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
const noon = (day: string) => new Date(atTime(day, "12:00"));
const monthTitle = (y: number, m: number) => new Date(y, m, 1).toLocaleDateString("ru-RU", { month: "long", year: "numeric" });
const dayTitle = (day: string) => noon(day).toLocaleDateString("ru-RU", { weekday: "long", day: "numeric", month: "long" });
/** Сетка месяца с понедельника; пустые клетки до первого числа — null. */
const monthGrid = (y: number, m: number) => {
  const lead = (new Date(y, m, 1).getDay() + 6) % 7;
  const n = new Date(y, m + 1, 0).getDate();
  return [...Array<null>(lead).fill(null), ...Array.from({ length: n }, (_, i) => todayKey(new Date(y, m, i + 1)))];
};
const planMs = (day: string, start: string, end: string) => Math.max(0, atTime(day, end) - atTime(day, start));

type DayState = "off" | "planned" | "today" | "worked" | "missed";
const STATE: Record<DayState, { label: string; tone: Tone; dot: string }> = {
  off: { label: "Выходной", tone: "neutral", dot: "" },
  planned: { label: "Запланирована", tone: "info", dot: "bg-brand" },
  today: { label: "Сегодня", tone: "success", dot: "bg-brand" },
  worked: { label: "Отработана", tone: "success", dot: "bg-success" },
  missed: { label: "Нет проходов", tone: "danger", dot: "bg-danger" },
};

/** Смены сотрудника: календарь месяца с отметками, подробности выбранного дня и ближайшие смены. */
export const WorkerShiftsPage = () => {
  const { key } = useOutletContext<WorkerCtx>();
  const db = useDb();
  const today = todayKey();
  const [cursor, setCursor] = useState(() => { const d = new Date(); return { y: d.getFullYear(), m: d.getMonth() }; });
  const [selected, setSelected] = useState(today);
  const [dir, setDir] = useState(0);

  const mine = useMemo(() => new Map(db.shifts.filter((s) => s.workerId === key.workerId).map((s) => [s.day, s])), [db, key.workerId]);
  const iv = useMemo(() => buildIntervals(db), [db]);
  const stateOf = (day: string): DayState => {
    if (!mine.has(day)) return "off";
    if (day === today) return "today";
    if (day > today) return "planned";
    return workedMs(db, key.workerId, day, iv) > 0 ? "worked" : "missed";
  };
  const go = (delta: number) => { setDir(delta); setCursor(({ y, m }) => { const d = new Date(y, m + delta, 1); return { y: d.getFullYear(), m: d.getMonth() }; }); };

  const sh = mine.get(selected);
  const st = stateOf(selected);
  const passes = dayPasses(db, key.workerId, selected);
  const worked = sh ? workedMs(db, key.workerId, selected, iv) : 0;
  const plan = sh ? planMs(selected, sh.start, sh.end) : 0;
  const upcoming = [...mine.values()].filter((s) => s.day >= today).sort((a, b) => a.day.localeCompare(b.day)).slice(0, 5);
  const monthCount = [...mine.keys()].filter((d) => d.startsWith(`${cursor.y}-${String(cursor.m + 1).padStart(2, "0")}`)).length;

  return (
    <div>
      <PageHeader kicker={`${monthCount} ${plural(monthCount, "смена", "смены", "смен")} в этом месяце`} title="Смены" sub="График и отработанное время по дням" />
      <motion.div variants={stagger(0.06)} initial="hidden" animate="show" className="flex flex-col gap-3 sm:gap-4">
        <motion.div variants={fadeUp}>
          <Card className="overflow-hidden">
            <CardHeader>
              <CardTitle className="first-letter:uppercase">{monthTitle(cursor.y, cursor.m)}</CardTitle>
              <div className="flex gap-1">
                <Button variant="quiet" size="icon-sm" aria-label="Предыдущий месяц" onClick={() => go(-1)}><ChevronLeft /></Button>
                <Button variant="quiet" size="icon-sm" aria-label="Следующий месяц" onClick={() => go(1)}><ChevronRight /></Button>
              </div>
            </CardHeader>
            <div className="grid grid-cols-7 gap-1 px-3 pt-3 text-center text-xs text-muted-foreground sm:px-5">{WEEK.map((d) => <span key={d}>{d}</span>)}</div>
            <AnimatePresence mode="wait" initial={false}>
              <motion.div key={`${cursor.y}-${cursor.m}`} initial={{ opacity: 0, x: dir * distance.slide }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: dir * -distance.slide, transition: tween.exit }} transition={{ ...spring.glide, opacity: tween.fast }}
                className="grid grid-cols-7 gap-1 px-3 pb-3 pt-1 sm:px-5 sm:pb-5" role="grid" aria-label="Календарь смен">
                {monthGrid(cursor.y, cursor.m).map((day, i) => {
                  if (!day) return <span key={`e${i}`} />;
                  const s = stateOf(day);
                  const on = day === selected;
                  return (
                    <motion.button key={day} type="button" {...press} onClick={() => setSelected(day)} aria-pressed={on} aria-label={`${dayTitle(day)}: ${STATE[s].label}`}
                      className={cn("relative flex aspect-square flex-col items-center justify-center gap-1 rounded-sm text-sm font-medium tabular-nums outline-none transition-colors duration-fast focus-visible:ring-2 focus-visible:ring-ring",
                        on ? "text-white" : day === today ? "bg-accent text-accent-foreground ring-1 ring-ring" : s === "off" ? "text-subtle-foreground hover:bg-surface" : "bg-surface hover:bg-surface-hover")}>
                      {/* Подсветка выбранного дня одна на календарь и перетекает между клетками (layoutId) */}
                      {on && <motion.span layoutId="shift-day" aria-hidden className="absolute inset-0 rounded-sm bg-brand-deep" transition={spring.glide} />}
                      <span className="relative">{noon(day).getDate()}</span>
                      <span className={cn("relative size-1 rounded-full transition-colors duration-fast", s === "off" ? "bg-transparent" : on ? "bg-white" : STATE[s].dot)} />
                    </motion.button>
                  );
                })}
              </motion.div>
            </AnimatePresence>
            <div className="flex flex-wrap gap-x-4 gap-y-1 border-t border-border px-4 py-3 text-xs text-muted-foreground sm:px-6">
              {(["planned", "worked", "missed"] as const).map((k) => <span key={k} className="inline-flex items-center gap-1.5"><span className={cn("size-1.5 rounded-full", STATE[k].dot)} />{STATE[k].label}</span>)}
            </div>
          </Card>
        </motion.div>

        {/* Выбранный день */}
        <motion.div variants={fadeUp}>
          <Card>
            <CardHeader><CardTitle className="first-letter:uppercase">{dayTitle(selected)}</CardTitle>
              <AnimatePresence mode="wait" initial={false}>
                <motion.span key={st} variants={popIn} initial="hidden" animate="show" exit="exit"><Status tone={STATE[st].tone}>{STATE[st].label}</Status></motion.span>
              </AnimatePresence>
            </CardHeader>
            <AutoHeight><AnimatePresence mode="popLayout" initial={false}>
            <motion.div key={selected} initial={{ opacity: 0, y: distance.exit }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: tween.exit }} transition={{ ...spring.glide, opacity: tween.fast }}>
            {sh ? (
              <div className="flex flex-col gap-4 p-4 sm:p-6">
                <div className="flex items-end justify-between gap-3">
                  <div>
                    <div className="text-xs text-muted-foreground">Смена</div>
                    <div className="font-display text-2xl font-semibold tabular-nums tracking-display">{sh.start} – {sh.end}</div>
                  </div>
                  <div className="text-right text-sm text-muted-foreground">{durationRu(plan)}</div>
                </div>
                {selected <= today && <>
                  <Progress value={plan ? Math.min(1, worked / plan) : 0} />
                  <dl className="grid grid-cols-3 gap-2">
                    {[{ icon: LogIn, label: "Вход", value: passes.firstIn ? hhmm(passes.firstIn) : "—" }, { icon: LogOut, label: "Выход", value: passes.lastOut ? hhmm(passes.lastOut) : "—" }, { icon: Timer, label: "Отработано", value: durationRu(worked) }].map(({ icon: Icon, label, value }) => (
                      <div key={label} className="flex min-w-0 flex-col gap-1 rounded-md bg-muted p-3">
                        <dt className="flex items-center gap-1.5 text-xs text-muted-foreground"><Icon className="size-3.5" />{label}</dt>
                        <dd className="truncate text-sm font-medium tabular-nums">{value}</dd>
                      </div>
                    ))}
                  </dl>
                  {passes.checkpointId && <p className="text-xs text-muted-foreground">{siteOfCheckpoint(db, passes.checkpointId).name} · {api.checkpointName(db, passes.checkpointId)}</p>}
                </>}
              </div>
            ) : <EmptyState icon={<CalendarDays />} title="Смены нет" text="В этот день вы не в графике" className="py-8" />}
            </motion.div>
            </AnimatePresence></AutoHeight>
          </Card>
        </motion.div>

        {/* Ближайшие смены */}
        <motion.div variants={fadeUp}>
          <Card>
            <CardHeader><CardTitle>Ближайшие смены</CardTitle></CardHeader>
            {upcoming.length ? (
              <ul className="flex flex-col p-2 sm:p-3">
                {upcoming.map((s) => (
                  <li key={s.id}>
                    <motion.button type="button" {...press} aria-pressed={s.day === selected} onClick={() => { setSelected(s.day); const d = noon(s.day); setDir(0); setCursor({ y: d.getFullYear(), m: d.getMonth() }); }}
                      className="relative flex w-full min-w-0 items-center gap-3 rounded-md p-2.5 text-left outline-none transition-colors duration-fast hover:bg-surface focus-visible:ring-2 focus-visible:ring-ring">
                      {s.day === selected && <motion.span layoutId="shift-upcoming" aria-hidden className="absolute inset-0 rounded-md bg-surface" transition={spring.glide} />}
                      <span className={cn("relative flex size-11 shrink-0 flex-col items-center justify-center rounded-sm leading-none", s.day === today ? "bg-brand-deep text-white" : "bg-surface")}>
                        <span className="text-xs capitalize opacity-80">{noon(s.day).toLocaleDateString("ru-RU", { weekday: "short" })}</span>
                        <span className="mt-0.5 font-display text-lg font-semibold tabular-nums">{noon(s.day).getDate()}</span>
                      </span>
                      <span className="relative min-w-0 flex-1">
                        <span className="block text-sm font-medium tabular-nums">{s.start} – {s.end}</span>
                        <span className="block truncate text-xs text-muted-foreground">{noon(s.day).toLocaleDateString("ru-RU", { day: "numeric", month: "long" })} · {durationRu(planMs(s.day, s.start, s.end))}</span>
                      </span>
                      {s.day === today ? <Status tone="success" className="relative">Сегодня</Status> : <ChevronRight className="relative size-4 text-subtle-foreground" />}
                    </motion.button>
                  </li>
                ))}
              </ul>
            ) : <EmptyState icon={<CalendarDays />} title="Смен пока нет" text="График ещё не назначен" className="py-8" />}
          </Card>
        </motion.div>
      </motion.div>
    </div>
  );
};
