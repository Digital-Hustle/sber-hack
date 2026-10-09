import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { Info } from "lucide-react";
import { dateTimeRu, reviewApi, reviewKeys, statusView, waitingRu } from "@/entities/application";
import { stageView } from "@/entities/document";
import { AnimatedNumber, Button, Card, CardHeader, CardTitle, EmptyState, CardSkeleton, HeaderSkeleton, PageHeader, Progress, Status, TilesSkeleton } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { fadeUp, popIn, stagger } from "@/shared/config/motion";

const REFRESH_MS = 30_000;

/** FE-OP-10 / B-06: метрики очереди с формулами «как посчитано». Пустая БД — честные нули. */
export const MetricsPage = () => {
  const q = useQuery({ queryKey: reviewKeys.metrics, queryFn: reviewApi.metrics, refetchInterval: REFRESH_MS });
  if (q.isPending) return <div aria-busy="true" aria-label="Загрузка"><HeaderSkeleton /><TilesSkeleton /><div className="mt-4 grid gap-4 md:grid-cols-2"><CardSkeleton lines={5} /><CardSkeleton lines={5} /></div></div>;
  if (q.isError || !q.data) return <EmptyState title="Метрики не загрузились" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />;
  const m = q.data;
  const share = m.fieldsTotal ? 1 - (m.fieldsCorrected ?? 0) / m.fieldsTotal : 0;
  const kpi = [
    { label: "Ожидание проверки, медиана", value: waitingRu(m.waiting?.p50), sub: `p95 — ${waitingRu(m.waiting?.p95)} · ${m.waiting?.count ?? 0} заявок` },
    { label: "До решения, медиана", value: waitingRu(m.decision?.p50), sub: `p95 — ${waitingRu(m.decision?.p95)} · ${m.decision?.count ?? 0} решений` },
    { label: "Поля без правок оператора", value: `${Math.round(share * 100)}%`, sub: `исправлено ${m.fieldsCorrected ?? 0} из ${m.fieldsTotal ?? 0}` },
  ];
  const group = (title: string, data: Record<string, number> | undefined, view: (k: string) => { label: string }) => {
    const rows = Object.entries(data ?? {});
    const sum = rows.reduce((acc, [, v]) => acc + v, 0);
    return (
      <motion.div variants={fadeUp} className="min-w-0">
        <Card className="h-full">
          <CardHeader><CardTitle>{title}</CardTitle><Status>{sum}</Status></CardHeader>
          {!rows.length ? <p className="px-4 pb-5 pt-3 text-sm text-muted-foreground sm:px-6">Пока нет данных</p> : (
            <ul className="flex flex-col gap-3 px-4 pb-5 pt-4 sm:px-6">
              {rows.map(([k, v]) => (
                <li key={k} className="flex flex-col gap-1.5">
                  <div className="flex items-baseline justify-between gap-3 text-sm"><span className="truncate text-muted-foreground">{view(k).label}</span><span className="font-semibold tabular-nums"><AnimatedNumber value={v} /></span></div>
                  <Progress value={sum ? v / sum : 0} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </motion.div>
    );
  };
  return (
    <>
      <PageHeader title="Метрики очереди" sub={`Пересчитано ${dateTimeRu(m.computedAt)}`} />
      <motion.dl variants={stagger(0.06)} initial="hidden" animate="show" className="grid gap-3 sm:gap-4 md:grid-cols-3">
        {kpi.map((k, i) => (
          <motion.div key={k.label} variants={popIn} className={`relative flex min-w-0 flex-col gap-1 overflow-hidden rounded-xl px-4 py-4 shadow-card sm:px-6 sm:py-5 ${i === 0 ? "bg-brand-deep text-white" : "bg-card"}`}>
            {i === 0 && <span aria-hidden className="absolute inset-0 bg-sheen" />}
            <dt className={`relative truncate text-sm ${i === 0 ? "text-white/85" : "text-muted-foreground"}`}>{k.label}</dt>
            <dd className="relative font-display text-4xl font-semibold tabular-nums tracking-hero sm:text-5xl">{k.value}</dd>
            <dd className={`relative truncate text-xs ${i === 0 ? "text-white/80" : "text-muted-foreground"}`}>{k.sub}</dd>
          </motion.div>
        ))}
      </motion.dl>
      <motion.div variants={stagger(0.06, 0.1)} initial="hidden" animate="show" className="mt-3 grid gap-3 sm:mt-4 sm:gap-4 md:grid-cols-2">
        {group("Заявки по статусам", m.applications, statusView)}
        {group("Документы по стадиям", m.documents, stageView)}
      </motion.div>
      {m.formulas && Object.keys(m.formulas).length > 0 && (
        <Card className="mt-3 sm:mt-4">
          <CardHeader><CardTitle className="flex items-center gap-2"><Info className="size-4" />Как посчитано</CardTitle></CardHeader>
          <dl className="grid gap-3 px-4 pb-5 pt-4 text-sm sm:px-6">
            {Object.entries(m.formulas).map(([k, v]) => <div key={k}><dt className="font-mono text-xs text-muted-foreground">{k}</dt><dd>{v}</dd></div>)}
          </dl>
        </Card>
      )}
    </>
  );
};
