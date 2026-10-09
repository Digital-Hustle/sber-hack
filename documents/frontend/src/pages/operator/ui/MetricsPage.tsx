import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { Info } from "lucide-react";
import { dateTimeRu, reviewApi, reviewKeys, statusView, waitingRu } from "@/entities/application";
import { stageView } from "@/entities/document";
import { AnimatedNumber, Button, Card, EmptyState, PageHeader, RowsSkeleton } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { fadeUp, stagger } from "@/shared/config/motion";

const REFRESH_MS = 30_000;

/** FE-OP-10 / B-06: метрики очереди с формулами «как посчитано». Пустая БД — честные нули. */
export const MetricsPage = () => {
  const q = useQuery({ queryKey: reviewKeys.metrics, queryFn: reviewApi.metrics, refetchInterval: REFRESH_MS });
  if (q.isPending) return <RowsSkeleton rows={6} />;
  if (q.isError || !q.data) return <EmptyState title="Метрики не загрузились" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />;
  const m = q.data;
  const share = m.fieldsTotal ? 1 - (m.fieldsCorrected ?? 0) / m.fieldsTotal : 0;
  const kpi = [
    { label: "Ожидание проверки, медиана", value: waitingRu(m.waiting?.p50), sub: `p95 — ${waitingRu(m.waiting?.p95)} · ${m.waiting?.count ?? 0} заявок` },
    { label: "До решения, медиана", value: waitingRu(m.decision?.p50), sub: `p95 — ${waitingRu(m.decision?.p95)} · ${m.decision?.count ?? 0} решений` },
    { label: "Поля без правок оператора", value: `${Math.round(share * 100)}%`, sub: `исправлено ${m.fieldsCorrected ?? 0} из ${m.fieldsTotal ?? 0}` },
  ];
  const group = (title: string, data: Record<string, number> | undefined, view: (k: string) => { label: string }) => (
    <Card className="p-4 sm:p-5">
      <h2 className="mb-3 font-semibold">{title}</h2>
      {!data || !Object.keys(data).length ? <p className="text-sm text-muted-foreground">Пока нет данных</p> : (
        <ul className="flex flex-col gap-2">
          {Object.entries(data).map(([k, v]) => <li key={k} className="flex items-center justify-between gap-3 text-sm"><span className="text-muted-foreground">{view(k).label}</span><span className="font-semibold tabular-nums"><AnimatedNumber value={v} /></span></li>)}
        </ul>
      )}
    </Card>
  );
  return (
    <>
      <PageHeader title="Метрики очереди" sub={`Пересчитано ${dateTimeRu(m.computedAt)}`} />
      <motion.div variants={stagger()} initial="hidden" animate="show" className="mb-4 grid gap-3 md:grid-cols-3">
        {kpi.map((k) => (
          <motion.div key={k.label} variants={fadeUp}><Card className="p-4 sm:p-5"><div className="text-sm text-muted-foreground">{k.label}</div><div className="mt-1 font-display text-3xl font-semibold tracking-display">{k.value}</div><div className="mt-1 text-xs text-muted-foreground">{k.sub}</div></Card></motion.div>
        ))}
      </motion.div>
      <div className="grid gap-3 md:grid-cols-2">
        {group("Заявки по статусам", m.applications, statusView)}
        {group("Документы по стадиям", m.documents, stageView)}
      </div>
      {m.formulas && Object.keys(m.formulas).length > 0 && (
        <Card className="mt-4 p-4 sm:p-5">
          <h2 className="mb-3 flex items-center gap-2 font-semibold"><Info className="size-4" />Как посчитано</h2>
          <dl className="grid gap-2 text-sm">
            {Object.entries(m.formulas).map(([k, v]) => <div key={k}><dt className="font-mono text-xs text-muted-foreground">{k}</dt><dd>{v}</dd></div>)}
          </dl>
        </Card>
      )}
    </>
  );
};
