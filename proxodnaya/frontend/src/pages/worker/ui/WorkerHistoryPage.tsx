import { useMemo, useState } from "react";
import { useOutletContext } from "react-router";
import { motion } from "motion/react";
import { History } from "lucide-react";
import { api, useDb, buildIntervals, workedMs, lastDays, siteOfCheckpoint, type Attempt } from "@/shared/api";
import { AttemptRow } from "@/entities/pass";
import { Card, EmptyState, LoadMore, PageHeader, RowsSkeleton, Segmented, AnimatedNumber } from "@/shared/ui";
import { dayKey, todayKey } from "@/shared/lib";
import { usePaged } from "@/shared/hooks";
import { fadeUp, popIn, stagger } from "@/shared/config/motion";
import type { WorkerCtx } from "@/widgets/worker-shell";

type Filter = "all" | "ALLOW" | "DENY";
const FILTERS: { value: Filter; label: string }[] = [{ value: "all", label: "Все" }, { value: "ALLOW", label: "Проходы" }, { value: "DENY", label: "Отказы" }];
const dayTitle = (day: string) => {
  if (day === todayKey()) return "Сегодня";
  if (day === lastDays(2)[0]) return "Вчера";
  const d = new Date(`${day}T12:00:00`);
  return d.toLocaleDateString("ru-RU", { weekday: "long", day: "numeric", month: "long" });
};

/** История проходов сотрудника: постранично с сервера (по 20), группы по дням, фильтр по решению. */
export const WorkerHistoryPage = () => {
  const { key } = useOutletContext<WorkerCtx>();
  const db = useDb();
  const [filter, setFilter] = useState<Filter>("all");
  const query = { mine: true, workerId: key.workerId, day: "all", decision: filter === "all" ? undefined : filter };
  const page = usePaged((cursor, limit) => api.queryAttempts({ ...query, cursor, limit }), JSON.stringify(query), { pageSize: 20, live: db.attempts.length, id: (a) => a.id });

  // Сводка за 7 дней считается на месте: она маленькая и нужна сразу, без ожидания списка.
  const week = useMemo(() => {
    const days = lastDays(7);
    const iv = buildIntervals(db);
    const from = new Date(`${days[0]}T00:00:00`).getTime();
    const mine = db.attempts.filter((a) => a.workerId === key.workerId && a.ts >= from);
    return {
      hours: days.reduce((s, d) => s + workedMs(db, key.workerId, d, iv), 0),
      days: new Set(mine.filter((a) => a.decision !== "DENY" && a.direction === "IN").map((a) => dayKey(a.ts))).size,
      deny: mine.filter((a) => a.decision === "DENY").length,
    };
  }, [db, key.workerId]);

  const groups = useMemo(() => page.items.reduce<[string, Attempt[]][]>((acc, a) => {
    const d = dayKey(a.ts);
    const last = acc[acc.length - 1];
    if (last?.[0] === d) last[1].push(a); else acc.push([d, [a]]);
    return acc;
  }, []), [page.items]);

  const kpi = [
    { label: "Отработано", value: <span>{Math.round(week.hours / 3600000)} ч</span> },
    { label: "Дней", value: <AnimatedNumber value={week.days} /> },
    { label: "Отказов", value: <AnimatedNumber value={week.deny} /> },
  ];

  return (
    <div>
      <PageHeader kicker="За последние 7 дней" title="История" sub="Проходы на всех ваших объектах" />
      <motion.div variants={stagger(0.06)} initial="hidden" animate="show" className="flex flex-col gap-3 sm:gap-4">
        <dl className="grid grid-cols-3 gap-2 sm:gap-3">
          {kpi.map(({ label, value }, i) => (
            <motion.div key={label} variants={popIn} className={`relative flex min-w-0 flex-col gap-1 overflow-hidden rounded-lg px-3 py-3 shadow-card sm:px-4 ${i === 0 ? "bg-brand-deep text-white" : "bg-card"}`}>
              {i === 0 && <span aria-hidden className="absolute inset-0 bg-sheen" />}
              <dt className={`relative truncate text-xs ${i === 0 ? "text-white/85" : "text-muted-foreground"}`}>{label}</dt>
              <dd className="relative truncate font-display text-xl font-semibold tabular-nums tracking-display">{value}</dd>
            </motion.div>
          ))}
        </dl>

        <motion.div variants={fadeUp}><Segmented block value={filter} onChange={setFilter} options={FILTERS} label="Фильтр" /></motion.div>

        {!page.ready ? <Card><RowsSkeleton rows={6} /></Card>
          : page.total === 0 ? <Card><EmptyState icon={<History />} title={filter === "DENY" ? "Отказов не было" : "Проходов пока нет"} text="Здесь появятся проходы, как только терминал отметит вас на проходной" /></Card>
          : (
            <div className="flex flex-col gap-4">
              {groups.map(([day, list]) => (
                <motion.section key={day} variants={fadeUp} initial="hidden" animate="show">
                  <h2 className="mb-2 px-1 text-sm font-medium text-muted-foreground first-letter:uppercase">{dayTitle(day)}</h2>
                  <Card className="divide-y divide-border px-4 sm:px-5">
                    {list.map((a) => <AttemptRow key={a.id} a={a} dirSource={siteOfCheckpoint(db, a.checkpointId).name} />)}
                  </Card>
                </motion.section>
              ))}
              <Card><LoadMore shown={page.items.length} total={page.total} hasMore={page.hasMore} loading={page.loading} error={page.error} onMore={page.more} className="border-t-0" /></Card>
            </div>
          )}
      </motion.div>
    </div>
  );
};
