import { useMemo, useState } from "react";
import { Link } from "react-router";
import { motion } from "motion/react";
import { UserPlus, ArrowRight } from "lucide-react";
import { useDb, presenceNow, dailyStats } from "@/shared/api";
import { Button, Card, CardHeader, CardTitle, Status, AnimatedNumber, Progress, PageHeader, type Tone } from "@/shared/ui";
import { todayKey, plural } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { fadeUp, popIn, stagger } from "@/shared/config/motion";
import { OnSiteNow } from "@/widgets/on-site";
import { LiveFeed } from "@/widgets/live-feed";

const todayRu = () => new Date().toLocaleDateString("ru-RU", { weekday: "long", day: "numeric", month: "long" });

export const DashboardPage = () => {
  const db = useDb();
  const presence = useMemo(() => presenceNow(db), [db]);
  const [allZones, setAllZones] = useState(false);
  // Зон может быть сколько угодно: при > 6 показываем самые загруженные, остальные — по кнопке.
  const zoneRows = useMemo(() => {
    const count = new Map<string, number>();
    for (const p of presence) count.set(p.zoneId, (count.get(p.zoneId) ?? 0) + 1);
    const rows = db.zones.map((z) => ({ z, n: count.get(z.id) ?? 0 }));
    return rows.length > 6 ? rows.sort((a, b) => b.n / Math.max(1, b.z.capacity) - a.n / Math.max(1, a.z.capacity)) : rows;
  }, [db.zones, presence]);
  const zonesShown = allZones ? zoneRows : zoneRows.slice(0, 6);
  const [today] = useMemo(() => dailyStats(db, [todayKey()]), [db]);
  const cap = db.zones.reduce((s, z) => s + z.capacity, 0);
  const insights = useMemo(() => {
    const out: { tone: Tone; text: string }[] = [];
    for (const z of db.zones) {
      const n = presence.filter((p) => p.zoneId === z.id).length;
      if (n > z.capacity * 0.8) out.push({ tone: "warning", text: `${z.name}: ${n} из ${z.capacity} — почти заполнено` });
    }
    const expired = db.workers.filter((w) => w.permitUntil < todayKey());
    if (expired.length) out.push({ tone: "danger", text: `${expired.length} ${plural(expired.length, "сотрудник", "сотрудника", "сотрудников")} с просроченным допуском — киоск их не пустит` });
    if (today.denySystem > 2) out.push({ tone: "info", text: `Сегодня ${today.denySystem} ${plural(today.denySystem, "системный отказ", "системных отказа", "системных отказов")} из-за качества кадра — проверьте освещение у камеры` });
    if (today.late) out.push({ tone: "info", text: `Опоздали сегодня: ${today.late}. Подробнее — в разделе «Помощник»` });
    return out;
  }, [db, presence, today]);

  const kpi = [
    { label: "На объекте", value: presence.length, sub: `из ${cap} мест` },
    { label: "Проходов сегодня", value: today.allow, sub: `${today.attempts} попыток` },
    { label: "Отказов сегодня", value: today.deny, sub: `${today.denySystem} системных` },
    { label: "Опоздали", value: today.late, sub: `${today.overtime} переработок` },
  ];

  return (
    <div>
      <PageHeader kicker={<span className="first-letter:uppercase">{todayRu()}</span>} title="Обстановка на объекте" sub="Данные обновляются в реальном времени при каждом проходе"
        actions={<Link to={routes.adminPersonNew} tabIndex={-1}><Button><UserPlus />Новый сотрудник</Button></Link>} />

      <motion.div variants={stagger(0.06)} initial="hidden" animate="show">
        {/* KPI плитками, первая — акцентная в фирменном градиенте (как плитки sberbank.ru) */}
        <dl className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
          {kpi.map(({ label, value, sub }, i) => (
            <motion.div key={label} variants={popIn} className={`relative flex min-w-0 flex-col gap-1 overflow-hidden rounded-xl px-4 py-4 shadow-card sm:px-6 sm:py-5 ${i === 0 ? "bg-brand-deep text-white" : "bg-card"}`}>
              {i === 0 && <span aria-hidden className="absolute inset-0 bg-sheen" />}
              <dt className={`relative truncate text-sm ${i === 0 ? "text-white/85" : "text-muted-foreground"}`}>{label}</dt>
              <dd className="relative font-display text-4xl font-semibold tabular-nums tracking-hero sm:text-5xl"><AnimatedNumber value={value} /></dd>
              <dd className={`relative truncate text-xs ${i === 0 ? "text-white/80" : "text-muted-foreground"}`}>{sub}</dd>
            </motion.div>
          ))}
        </dl>
      </motion.div>

      <motion.div variants={stagger(0.06, 0.1)} initial="hidden" animate="show" className="mt-3 grid gap-3 sm:mt-4 sm:gap-4 lg:grid-cols-5">
        <motion.div variants={fadeUp} className="min-w-0 lg:col-span-3">
          <Card className="h-full">
            <CardHeader><CardTitle>Сейчас на объекте</CardTitle><Status tone="success">{presence.length}</Status></CardHeader>
            <div className="grid gap-3 px-4 pt-4 sm:grid-cols-3 sm:px-6">
              {zonesShown.map(({ z, n }) => {
                const k = n / Math.max(1, z.capacity);
                return (
                  <div key={z.id} className="flex min-w-0 flex-col gap-2 rounded-md bg-muted p-3">
                    <div className="flex items-baseline justify-between gap-2 text-sm"><span className="truncate text-muted-foreground">{z.name}</span><span className="shrink-0 font-medium tabular-nums">{n}<span className="text-muted-foreground">/{z.capacity}</span></span></div>
                    <Progress value={k} tone={k > 0.8 ? "warning" : "brand"} />
                  </div>
                );
              })}
            </div>
            {zoneRows.length > 6 && <div className="px-4 pt-2 sm:px-6"><Button size="sm" variant="quiet" onClick={() => setAllZones((v) => !v)}>{allZones ? "Свернуть зоны" : `Все зоны · ${zoneRows.length}`}</Button></div>}
            <div className="p-2 sm:p-4"><OnSiteNow /></div>
          </Card>
        </motion.div>
        <div className="flex min-w-0 flex-col gap-3 sm:gap-4 lg:col-span-2">
          {insights.length > 0 && (
            <motion.div variants={fadeUp}>
              <Card>
                <CardHeader><CardTitle>Рекомендации помощника</CardTitle></CardHeader>
                <motion.ul variants={stagger(0.05, 0.3)} initial="hidden" animate="show" className="flex flex-col gap-1 p-2 sm:px-3 sm:pb-3">
                  {insights.map((i) => (
                    <motion.li key={i.text} variants={fadeUp} className="flex items-start gap-3 rounded-md px-2 py-2 text-sm transition-colors duration-fast hover:bg-muted">
                      <span className={`mt-1.5 size-2 shrink-0 rounded-full ${{ warning: "bg-warning", danger: "bg-danger", info: "bg-info", success: "bg-success", neutral: "bg-subtle-foreground" }[i.tone]}`} />
                      <span className="min-w-0 text-pretty">{i.text}</span>
                    </motion.li>
                  ))}
                </motion.ul>
              </Card>
            </motion.div>
          )}
          <motion.div variants={fadeUp} className="min-w-0">
            <Card className="overflow-hidden">
              <CardHeader className="pb-2"><CardTitle>Последние проходы</CardTitle>
                <Link to={routes.adminJournal} className="flex shrink-0 items-center gap-1 rounded-sm text-sm font-medium text-accent-foreground hover:underline">Журнал<ArrowRight className="size-4" /></Link>
              </CardHeader>
              <LiveFeed limit={7} />
            </Card>
          </motion.div>
        </div>
      </motion.div>
    </div>
  );
};
