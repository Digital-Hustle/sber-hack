import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, Info } from "lucide-react";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, Card, CardContent, DateRangePicker, EmptyState, PageHeader, Progress, RowsSkeleton, Select, type DateRange } from "@/shared/ui";
import { todayKey } from "@/shared/lib";

const PERCENT = 100;
const DAY_MS = 86_400_000;
const daysAgo = (n: number) => todayKey(new Date(Date.now() - n * DAY_MS));

/** FE-SP-08: частые ошибки по правилам за период; доли и метод расчёта приходят с сервера. */
export const StatsPage = () => {
  const [range, setRange] = useState<DateRange>({ from: daysAgo(30), to: todayKey() });
  const [program, setProgram] = useState("");
  const programs = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs });
  const f = { from: range.from, to: range.to, program: program || undefined };
  const q = useQuery({ queryKey: protocolKeys.stats(f), queryFn: () => protocolApi.stats(f) });
  const options = [{ value: "", label: "Все программы" }, ...(programs.data?.items ?? []).map((p) => ({ value: p.code, label: p.name }))];
  const presets = [{ label: "7 дней", range: { from: daysAgo(7), to: todayKey() } }, { label: "30 дней", range: { from: daysAgo(30), to: todayKey() } }, { label: "90 дней", range: { from: daysAgo(90), to: todayKey() } }];

  return (
    <>
      <PageHeader title="Частые ошибки" sub="Какие проверки чаще всего не проходят пакеты — чтобы заранее подсказывать организациям" />
      <div className="mb-4 flex flex-col gap-2 lg:flex-row lg:items-center">
        <DateRangePicker aria-label="Период" value={range} onChange={setRange} max={todayKey()} presets={presets} />
        <Select aria-label="Программа" className="lg:w-72" value={program} options={options} onChange={setProgram} />
      </div>
      {q.isPending ? <RowsSkeleton rows={6} /> : q.isError ? (
        <EmptyState title="Статистика не загрузилась" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : (
        <Card>
          <CardContent className="flex flex-col gap-4">
            <p className="text-sm text-muted-foreground">Пакетов с протоколом за период: <span className="tabular-nums text-foreground">{q.data.packages}</span></p>
            {!q.data.items.length ? <EmptyState icon={<BarChart3 />} title="Ошибок нет" text="За период ни одно правило не было нарушено" /> : (
              <ul className="flex flex-col gap-3">
                {q.data.items.map((s) => {
                  const pct = Number(s.share) * PERCENT;
                  return (
                    <li key={s.rule} className="flex flex-col gap-1">
                      <div className="flex items-center gap-2 text-sm"><span className="flex-1 font-medium">{s.rule}</span><span className="tabular-nums text-muted-foreground">{s.count} · {pct.toFixed(1)}%</span></div>
                      <Progress value={pct} tone="danger" />
                    </li>
                  );
                })}
              </ul>
            )}
            <p className="flex gap-2 rounded-md bg-surface p-3 text-xs text-muted-foreground"><Info className="size-4 shrink-0" />Как посчитано: {q.data.method}</p>
          </CardContent>
        </Card>
      )}
    </>
  );
};
