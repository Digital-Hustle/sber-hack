import { useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { motion } from "motion/react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, Area, AreaChart } from "recharts";
import { Info } from "lucide-react";
import { useDb, dailyStats, lastDays, loadByHour, refusalsByCode, unclosedIntervals, presenceTtlHours, REASONS } from "@/shared/api";
import { Card, CardHeader, CardTitle, Dialog, DateRangePicker, Button, AnimatedNumber, PageHeader, Avatar, Status } from "@/shared/ui";
import { atTime, cn, dateRu, hhmm, plural, todayKey } from "@/shared/lib";

const span = (from: string, to: string) => { const out: string[] = []; const [y, m, d] = from.split("-").map(Number); for (let x = new Date(y, m - 1, d); todayKey(x) <= to && out.length < 93; x.setDate(x.getDate() + 1)) out.push(todayKey(x)); return out; };
import { cssVar } from "@/shared/config/tokens";
import { fadeUp, stagger, spring } from "@/shared/config/motion";

const axis = { stroke: cssVar("muted-foreground"), fontSize: 12, tickLine: false, axisLine: false } as const;
const tip = { contentStyle: { background: cssVar("popover"), border: `1px solid ${cssVar("border")}`, borderRadius: 12, color: cssVar("popover-foreground"), fontSize: 13 }, cursor: { fill: cssVar("muted") } } as const;
const anim = { animationDuration: 700, animationEasing: "ease-out" } as const;

const HOW = [
  ["Отработано", "Сумма пересечений интервалов «вход — выход» со сменой с учётом допуска. Если выход не отмечен, время считается не дальше конца смены."],
  ["Незакрытые интервалы", "Вход без выхода дольше срока из настроек. Такие сотрудники не считаются находящимися на объекте, а их следующий проход будет записан как вход."],
  ["Опоздание", "Первый вход позже начала смены больше чем на 5 минут."],
  ["Переработка", "Последний выход позже конца смены больше чем на 5 минут."],
  ["Отказы", "Делятся на правомерные (нарушены правила допуска, лицо не совпало, QR уже использован) и системные (недостаточное качество кадра, лицо не найдено). Доля системных отказов показывает качество работы оборудования."],
  ["Нагрузка", "Количество успешных входов и выходов по часам."],
];

const Legend = ({ items }: { items: [string, string][] }) => (
  <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">{items.map(([c, l]) => <span key={l} className="flex items-center gap-1.5"><span className={cn("size-2.5 rounded-full", c)} />{l}</span>)}</div>
);

export const AnalyticsPage = () => {
  const db = useDb();
  const [q, setQ] = useSearchParams();
  const def = lastDays(7);
  const period = { from: q.get("from") ?? def[0], to: q.get("to") ?? def[6] };
  const setPeriod = (r: { from: string; to: string }) => setQ((p) => { p.set("from", r.from); p.set("to", r.to); return p; }, { replace: true });
  const today = todayKey();
  const [how, setHow] = useState(false);
  const range = useMemo(() => span(period.from, period.to), [period.from, period.to]);
  const toTs = atTime(period.to, "23:59") + 60000;
  const from = atTime(range[0], "00:00");
  const stats = useMemo(() => dailyStats(db, range).map((d) => ({ ...d, label: d.day.slice(8) + "." + d.day.slice(5, 7) })), [db, range]);
  const load = useMemo(() => loadByHour({ ...db, attempts: db.attempts.filter((a) => a.ts < toTs) }, from).filter((h) => h.hour >= 5 && h.hour <= 23), [db, from, toTs]);
  const refusals = useMemo(() => refusalsByCode({ ...db, attempts: db.attempts.filter((a) => a.ts < toTs) }, from).map((r) => ({ ...r, name: REASONS[r.code].message })).sort((a, b) => b.count - a.count), [db, from, toTs]);
  const maxRef = Math.max(1, ...refusals.map((r) => r.count));
  const unclosed = useMemo(() => unclosedIntervals(db).filter((i) => i.start >= from && i.start < toTs), [db, from, toTs]);
  const ttl = presenceTtlHours(db);
  const who = (id: string) => db.workers.find((w) => w.id === id);
  const total = stats.reduce((s, d) => s + d.attempts, 0);
  const deny = stats.reduce((s, d) => s + d.deny, 0);
  const sys = stats.reduce((s, d) => s + d.denySystem, 0);
  const pctFmt = (n: number) => `${Math.round(n)}%`;
  const kpi = [
    { label: "Часов отработано", value: Math.round(stats.reduce((s, d) => s + d.hours, 0)) },
    { label: "Попыток прохода", value: total },
    { label: "Доля отказов", value: total ? (deny / total) * 100 : 0, fmt: pctFmt },
    { label: "Из них системных", value: deny ? (sys / deny) * 100 : 0, fmt: pctFmt },
  ];

  return (
    <div>
      <PageHeader title="Аналитика" sub="Показатели рассчитаны по событиям журнала проходов"
        actions={<><DateRangePicker value={period} onChange={setPeriod} max={today} aria-label="Период" className="h-control-sm w-56 text-sm" presets={[
          { label: "Сегодня", range: { from: today, to: today } },
          { label: "7 дней", range: { from: def[0], to: today } },
          { label: "14 дней", range: { from: lastDays(14)[0], to: today } },
          { label: "30 дней", range: { from: lastDays(30)[0], to: today } },
        ]} /><Button variant="quiet" size="sm" onClick={() => setHow(true)}><Info />Как посчитано</Button></>} />
      <motion.div variants={fadeUp} initial="hidden" animate="show"><Card className="mb-3 sm:mb-4">
        <dl className="grid grid-cols-2 lg:grid-cols-4">
          {kpi.map(({ label, value, fmt }, i) => (
            <div key={label} className={`flex min-w-0 flex-col gap-1 px-4 py-4 sm:px-6 sm:py-6 ${i % 2 ? "border-l border-border" : ""} ${i > 1 ? "border-t border-border lg:border-t-0" : ""} ${i === 2 ? "lg:border-l" : ""}`}>
              <dt className="order-2 truncate text-sm text-muted-foreground">{label}</dt>
              <dd className="font-display text-3xl font-semibold tabular-nums tracking-display sm:text-4xl"><AnimatedNumber value={value} format={fmt} /></dd>
            </div>
          ))}
        </dl>
      </Card></motion.div>
      <motion.div variants={stagger(0.06)} initial="hidden" animate="show" className="grid gap-3 sm:gap-4 lg:grid-cols-2">
        <motion.div variants={fadeUp} className="min-w-0"><Card className="h-full">
          <CardHeader><CardTitle>Отработано часов по дням</CardTitle></CardHeader>
          <div className="h-64 px-2 pb-3 pt-4 sm:h-72 sm:px-4"><ResponsiveContainer><BarChart data={stats} margin={{ left: -12, right: 4 }}><CartesianGrid vertical={false} stroke={cssVar("border")} /><XAxis dataKey="label" {...axis} interval="preserveStartEnd" minTickGap={8} /><YAxis {...axis} width={40} /><Tooltip {...tip} /><Bar dataKey="hours" name="часы" fill={cssVar("chart-1")} radius={[6, 6, 0, 0]} maxBarSize={36} {...anim} /></BarChart></ResponsiveContainer></div>
        </Card></motion.div>
        <motion.div variants={fadeUp} className="min-w-0"><Card className="h-full">
          <CardHeader className="flex-wrap"><CardTitle>Нагрузка на проходную</CardTitle><Legend items={[["bg-chart-1", "входы"], ["bg-chart-2", "выходы"]]} /></CardHeader>
          <div className="h-64 px-2 pb-3 pt-4 sm:h-72 sm:px-4"><ResponsiveContainer><AreaChart data={load} margin={{ left: -12, right: 4 }}><CartesianGrid vertical={false} stroke={cssVar("border")} /><XAxis dataKey="hour" {...axis} interval="preserveStartEnd" minTickGap={8} /><YAxis {...axis} width={40} /><Tooltip {...tip} />
            <Area type="monotone" dataKey="in" name="входы" stroke={cssVar("chart-1")} fill={cssVar("chart-1")} fillOpacity={0.18} strokeWidth={2} {...anim} />
            <Area type="monotone" dataKey="out" name="выходы" stroke={cssVar("chart-2")} fill={cssVar("chart-2")} fillOpacity={0.14} strokeWidth={2} {...anim} /></AreaChart></ResponsiveContainer></div>
        </Card></motion.div>
        <motion.div variants={fadeUp} className="min-w-0"><Card className="h-full">
          <CardHeader className="flex-wrap"><CardTitle>Отказы по причинам</CardTitle><Legend items={[["bg-danger", "правомерные"], ["bg-chart-4", "системные"]]} /></CardHeader>
          <ul className="flex flex-col gap-3 p-4 sm:p-6">
            {refusals.length === 0 && <li className="text-sm text-muted-foreground">Отказов не было</li>}
            {refusals.map((r, i) => (
              <li key={r.code} className="flex min-w-0 flex-col gap-1.5">
                <div className="flex items-baseline justify-between gap-3 text-sm"><span className="min-w-0 truncate">{r.name}</span><span className="shrink-0 font-medium tabular-nums">{r.count}</span></div>
                <div className="h-2 overflow-hidden rounded-full bg-surface">
                  <motion.div className={cn("h-full origin-left rounded-full", r.system ? "bg-chart-4" : "bg-danger")} initial={{ scaleX: 0 }} animate={{ scaleX: r.count / maxRef }} transition={{ ...spring.bar, delay: i * 0.04 }} />
                </div>
              </li>
            ))}
          </ul>
        </Card></motion.div>
        <motion.div variants={fadeUp} className="min-w-0"><Card className="h-full">
          <CardHeader className="flex-wrap"><CardTitle>Опоздания и переработки</CardTitle><Legend items={[["bg-chart-5", "опоздания"], ["bg-chart-2", "переработки"]]} /></CardHeader>
          <div className="h-64 px-2 pb-3 pt-4 sm:h-72 sm:px-4"><ResponsiveContainer><BarChart data={stats} margin={{ left: -12, right: 4 }}><CartesianGrid vertical={false} stroke={cssVar("border")} /><XAxis dataKey="label" {...axis} interval="preserveStartEnd" minTickGap={8} /><YAxis {...axis} width={40} allowDecimals={false} /><Tooltip {...tip} />
            <Bar dataKey="late" name="опоздания" fill={cssVar("chart-5")} radius={[4, 4, 0, 0]} maxBarSize={20} {...anim} /><Bar dataKey="overtime" name="переработки" fill={cssVar("chart-2")} radius={[4, 4, 0, 0]} maxBarSize={20} {...anim} /></BarChart></ResponsiveContainer></div>
        </Card></motion.div>
        <motion.div variants={fadeUp} className="min-w-0 lg:col-span-2"><Card>
          <CardHeader className="flex-wrap"><CardTitle>Незакрытые интервалы</CardTitle><Status tone={unclosed.length ? "warning" : "success"}>{unclosed.length ? `${unclosed.length} ${plural(unclosed.length, "интервал", "интервала", "интервалов")}` : "нет"}</Status></CardHeader>
          <p className="px-4 pt-3 text-sm text-muted-foreground sm:px-6">Вход без отметки выхода дольше {ttl} {plural(ttl, "часа", "часов", "часов")}. В текущее присутствие не входит, отработанное время учтено до конца смены.</p>
          {unclosed.length === 0 ? <div className="px-4 pb-5 pt-3 text-sm sm:px-6">Все интервалы за период закрыты выходом</div> : (
            <motion.ul variants={stagger(0.04)} initial="hidden" animate="show" className="grid gap-2 p-4 sm:grid-cols-2 sm:p-6 lg:grid-cols-3">
              {unclosed.map((i) => { const w = who(i.workerId); return (
                <motion.li key={`${i.workerId}${i.start}`} variants={fadeUp} className="flex min-w-0 items-center gap-3 rounded-md bg-muted p-3">
                  <Avatar name={w?.fullName ?? "?"} photo={w?.photo} className="size-9 text-xs" />
                  <div className="min-w-0"><div className="truncate text-sm font-medium">{w?.fullName ?? "Неизвестный сотрудник"}</div><div className="truncate text-xs text-muted-foreground">вход {dateRu(i.start)}, {hhmm(i.start)} · выход не отмечен</div></div>
                </motion.li>
              ); })}
            </motion.ul>
          )}
        </Card></motion.div>
      </motion.div>
      <Dialog open={how} onClose={() => setHow(false)} title="Как посчитано">
        <dl className="flex flex-col divide-y divide-border">{HOW.map(([k, v]) => <div key={k} className="py-3 first:pt-0 last:pb-0"><dt className="text-sm font-medium">{k}</dt><dd className="mt-0.5 text-sm text-muted-foreground">{v}</dd></div>)}</dl>
      </Dialog>
    </div>
  );
};
