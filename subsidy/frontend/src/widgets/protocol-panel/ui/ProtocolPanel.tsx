import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, ClipboardCopy, FileSearch } from "lucide-react";
import { ProtocolStatusBadge } from "@/entities/package";
import { CHECK_STATUS, protocolApi, protocolKeys, ruleView, type Protocol, type RuleResult } from "@/entities/protocol";
import { ApiError, errorText } from "@/shared/api";
import { Button, Card, CardContent, CardHeader, CardTitle, EmptyState, Progress, RowsSkeleton, Segmented, Select, Spinner, Status, toast } from "@/shared/ui";
import { agoRu } from "@/shared/lib";

type Group = "problems" | "all";

const Completeness = ({ p }: { p: Protocol }) => {
  const c = p.completeness;
  const copy = () => { if (c.request) void navigator.clipboard.writeText(c.request).then(() => toast.success("Текст запроса скопирован")); };
  return (
    <Card>
      <CardHeader><CardTitle>Комплект документов · {c.percent}%</CardTitle></CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Progress value={c.percent} />
        <ul className="flex flex-col gap-1.5">
          {c.items.map((it) => {
            const v = CHECK_STATUS[it.status] ?? { label: it.status, tone: "neutral" as const };
            return (
              <li key={it.item} className="flex items-start gap-2 text-sm">
                <span className="min-w-0 flex-1">{it.title}{it.condition && <span className="text-muted-foreground"> · {it.condition}</span>}</span>
                <Status tone={v.tone}>{v.label}</Status>
              </li>
            );
          })}
        </ul>
        {c.request && (
          <div className="flex flex-col gap-2 rounded-md bg-surface p-3">
            <div className="text-sm font-medium">Запрос организации</div>
            <p className="whitespace-pre-wrap text-sm">{c.request}</p>
            <Button size="sm" variant="secondary" className="self-start" onClick={copy}><ClipboardCopy />Скопировать</Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

const Result = ({ r, onEvidence }: { r: RuleResult; onEvidence: (documentId: string, page: number | null) => void }) => {
  const v = ruleView(r.status);
  return (
    <li className="flex flex-col gap-1 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <Status tone={v.tone} dot>{v.label}</Status>
        <span className="text-xs text-muted-foreground">{r.rule}</span>
      </div>
      <p className="text-sm">{r.message}</p>
      {(r.expected || r.actual) && (
        <div className="flex flex-col text-sm">
          {r.expected && <div><span className="text-muted-foreground">ожидалось: </span><span className="tabular-nums">{r.expected}</span></div>}
          {r.actual && <div><span className="text-muted-foreground">в документах: </span><span className="tabular-nums">{r.actual}</span></div>}
        </div>
      )}
      {!!r.evidence?.length && (
        <div className="flex flex-wrap gap-1">
          {r.evidence.filter((e) => e.documentId).map((e, i) => (
            <Button key={i} size="sm" variant="ghost" onClick={() => onEvidence(e.documentId!, e.page)}><FileSearch />{e.field ?? "документ"}{e.page ? `, стр. ${e.page}` : ""}</Button>
          ))}
        </div>
      )}
    </li>
  );
};

/** FE-SP-03/04/06: протокол — полнота, «чего не хватает», результаты правил с expected/actual и переходом к документу, версии. */
export const ProtocolPanel = ({ packageId, onEvidence }: { packageId: string; onEvidence: (documentId: string, page: number | null) => void }) => {
  const [version, setVersion] = useState<string>("");
  const [group, setGroup] = useState<Group>("problems");
  const versions = useQuery({ queryKey: protocolKeys.versions(packageId), queryFn: () => protocolApi.versions(packageId) });
  const latest = useQuery({ queryKey: protocolKeys.latest(packageId), queryFn: () => protocolApi.latest(packageId), enabled: !version, retry: false });
  const chosen = useQuery({ queryKey: protocolKeys.version(packageId, Number(version)), queryFn: () => protocolApi.version(packageId, Number(version)), enabled: !!version });
  const q = version ? chosen : latest;

  if (q.isPending) return <RowsSkeleton rows={4} />;
  if (q.isError) {
    if (q.error instanceof ApiError && (q.error.code === "NOT_CHECKED_YET" || q.error.status === 404 || q.error.status === 409)) {
      return <Card><EmptyState icon={<Spinner className="size-6" />} title="Проверка идёт" text="Протокол появится, когда все документы будут разобраны" className="py-10" /></Card>;
    }
    return <EmptyState title="Протокол не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />;
  }
  const p = q.data;
  const results = [...p.results].sort((a, b) => ruleView(a.status).order - ruleView(b.status).order);
  const shown = group === "all" ? results : results.filter((r) => ruleView(r.status).order < ruleView("PASSED").order);
  const options = [{ value: "", label: "Последняя версия" }, ...(versions.data?.items ?? []).map((v) => ({ value: String(v.version), label: `Версия ${v.version}`, hint: agoRu(Date.parse(v.createdAt)) }))];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <ProtocolStatusBadge status={p.status} />
        <span className="text-sm text-muted-foreground">версия {p.version} · правила {p.rulesVersion} · {agoRu(Date.parse(p.createdAt))}</span>
        <span className="flex-1" />
        <Select aria-label="Версия протокола" size="sm" className="w-48" value={version} options={options} onChange={setVersion} />
      </div>
      <Completeness p={p} />
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-center gap-2">
          <CardTitle className="flex-1">Проверки · ошибок {p.failed}, предупреждений {p.warnings}, вручную {p.needsReview}</CardTitle>
          <Segmented size="sm" label="Показать" value={group} onChange={setGroup} options={[{ value: "problems", label: "Проблемы" }, { value: "all", label: "Все" }]} />
        </CardHeader>
        <CardContent>
          {!shown.length ? <EmptyState icon={<CheckCircle2 />} title="Проблем не найдено" text="Все применимые правила пройдены" /> : (
            <ul className="flex flex-col divide-y divide-border">{shown.map((r) => <Result key={r.rule} r={r} onEvidence={onEvidence} />)}</ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
};
