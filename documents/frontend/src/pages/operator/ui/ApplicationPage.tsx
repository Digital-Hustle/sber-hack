import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { AlertTriangle, ArrowLeft, Gavel, MessageSquarePlus, UserCheck, FileWarning, Timer, History, ListChecks } from "lucide-react";
import { AppStatusBadge, dateRuFull, dateTimeRu, reviewApi, reviewKeys, waitingRu, type FieldCard } from "@/entities/application";
import { StageBadge, confidenceTone, percent, reasonView } from "@/entities/document";
import { can, useSession } from "@/entities/session";
import { DecisionDialog } from "@/features/decide-application";
import { RequestDialog } from "@/features/request-resubmit";
import { DocumentActions, useAssign, useConfirmDocument } from "@/features/review-document";
import { ExportCardButtons } from "@/features/export-documents";
import { useTakeNext } from "@/features/take-next";
import { DocumentViewer } from "@/widgets/document-viewer";
import { FieldsPanel } from "@/widgets/fields-panel";
import { HistoryTimeline } from "@/widgets/history-timeline";
import { AnimatedNumber, Button, Card, EmptyState, RowsSkeleton, Segmented, Status } from "@/shared/ui";
import { ApiError, errorText, useEventStream } from "@/shared/api";
import { cn } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { fadeUp } from "@/shared/config/motion";

/** Сколько минут ручного ввода экономит одно поле, заполненное моделью (оценка для счётчика FE-OP-06, см. EVAL.md). */
const SECONDS_PER_FIELD = 20;

/** FE-OP-03..08: карточка заявки — оригинал рядом с полями, расхождения, история, действия. */
export const ApplicationPage = () => {
  const { id = "" } = useParams();
  const s = useSession();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: reviewKeys.card(id), queryFn: () => reviewApi.card(id) });
  const [docId, setDocId] = useState<string | null>(null);
  const [field, setField] = useState<FieldCard | null>(null);
  const [tab, setTab] = useState<"fields" | "history">("fields");
  const [decide, setDecide] = useState(false);
  const [ask, setAsk] = useState(false);
  const assign = useAssign(id);
  const confirm = useConfirmDocument(id);
  const next = useTakeNext();
  useEventStream("review", "/stream/ticket", "/stream", ["queue.changed", "document.stage"], (_, data) => {
    const d = data as { applicationId?: string } | null;
    if (!d?.applicationId || d.applicationId === id) void qc.invalidateQueries({ queryKey: reviewKeys.card(id) });
  });
  const card = q.data;
  const docs = useMemo(() => card?.documents ?? [], [card]);
  const doc = docs.find((d) => d.id === docId) ?? docs.find((d) => d.attention) ?? docs[0];
  const canAct = can(s.roles, "act");
  useEffect(() => { setField(null); }, [doc?.id]);
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLElement && e.target.closest("input, textarea, button, [role=dialog]")) return;
      if (e.key === "Enter" && canAct && doc && !doc.confirmed && !confirm.isPending) { e.preventDefault(); confirm.mutate(doc.id!); }
      if (e.key === "n" && !next.isPending) next.mutate();
    };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  }, [canAct, doc, confirm, next]);

  if (q.isPending) return <RowsSkeleton rows={8} />;
  if (q.isError || !card) {
    const nf = q.error instanceof ApiError && q.error.status === 404;
    return <EmptyState title={nf ? "Заявка не найдена" : "Карточка не загрузилась"} text={nf ? undefined : errorText(q.error)}
      action={<div className="flex gap-2"><Link to={routes.operator}><Button variant="secondary"><ArrowLeft />К очереди</Button></Link>{!nf && <Button onClick={() => q.refetch()}>Повторить</Button>}</div>} />;
  }
  const app = card.application ?? {};
  const c = card.counters ?? {};
  const mine = app.assignee === s.login;
  const decided = app.status === "ACCEPTED" || app.status === "RETURNED";
  const findings = card.findings ?? [];
  const typeTitle = (t: string) => docs.find((d) => d.type === t)?.typeName ?? t;
  const missing = (app.missing ?? []).map((t) => ({ type: t, title: typeTitle(t) }));
  const saved = Math.round((((c.fieldsTotal ?? 0) - (c.fieldsCorrected ?? 0)) * SECONDS_PER_FIELD) / 60);
  const reason = doc ? reasonView(doc.reasonCode) : null;

  return (
    <>
      <motion.div variants={fadeUp} initial="hidden" animate="show" className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end">
        <div className="min-w-0 flex-1">
          <Link to={routes.operator} className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Очередь</Link>
          <h1 className="truncate font-display text-2xl font-semibold tracking-display sm:text-3xl">{app.applicantName || "Соискатель"}</h1>
          <div className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <AppStatusBadge status={app.status} />
            <span>№ {id.slice(0, 8)}</span>
            {app.submittedAt && <span>отправлена {dateTimeRu(app.submittedAt)}</span>}
            {app.assignee && <Status><UserCheck />{mine ? "у вас" : app.assignee}</Status>}
            {card.contactPhone && <span>{card.contactPhone}</span>}
            {card.contactEmail && <span>{card.contactEmail}</span>}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {canAct && !mine && !decided && <Button variant="secondary" disabled={assign.isPending} onClick={() => assign.mutate(!!app.assignee)}><UserCheck />{app.assignee ? "Забрать себе" : "Взять в работу"}</Button>}
          {canAct && !decided && <Button variant="secondary" onClick={() => setAsk(true)}><MessageSquarePlus />Запросить</Button>}
          {can(s.roles, "export") && <ExportCardButtons appId={id} />}
          {can(s.roles, "decide") && !decided && <Button onClick={() => setDecide(true)}><Gavel />Решение</Button>}
        </div>
      </motion.div>

      <div className="mb-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
        {[
          { label: "Документы подтверждены", value: c.documentsConfirmed ?? 0, of: c.documentsTotal ?? 0 },
          { label: "Поля на перепроверку", value: c.fieldsAttention ?? 0, tone: (c.fieldsAttention ?? 0) > 0 },
          { label: "Исправлено оператором", value: c.fieldsCorrected ?? 0, of: c.fieldsTotal ?? 0 },
          { label: "Сэкономлено, мин", value: saved, hint: `≈ ${SECONDS_PER_FIELD} с ручного ввода на поле` },
        ].map((k) => (
          <Card key={k.label} className="p-3" title={k.hint}>
            <div className="text-xs text-muted-foreground">{k.label}</div>
            <div className={cn("mt-0.5 font-display text-2xl font-semibold tabular-nums", k.tone && "text-warning")}><AnimatedNumber value={k.value} />{k.of !== undefined && <span className="text-base text-muted-foreground"> / {k.of}</span>}</div>
          </Card>
        ))}
      </div>

      {(findings.length > 0 || card.decision) && (
        <Card className="mb-4 p-4">
          {card.decision && <p className="mb-2 text-sm"><b className="font-semibold">Решение:</b> {card.decision.decision === "ACCEPTED" ? "принята" : "возвращена"} · {card.decision.actor} · {dateTimeRu(card.decision.createdAt)}{card.decision.comment ? ` — «${card.decision.comment}»` : ""}</p>}
          {findings.length > 0 && <p className="mb-2 flex items-center gap-2 text-sm font-semibold"><FileWarning className="size-4 text-warning" />Расхождения и пробелы</p>}
          <ul className="flex flex-col gap-1.5">
            {findings.map((f, i) => {
              const v = reasonView(f.code, f.params ?? {});
              return (
                <li key={i} className="flex flex-wrap items-center gap-2 text-sm">
                  <Status tone={f.code === "MISSING_DOCUMENT" ? "danger" : "warning"}><AlertTriangle />{v?.text}</Status>
                  {f.documentIds?.map((d) => <Button key={d} size="sm" variant="quiet" onClick={() => setDocId(d)}>{docs.find((x) => x.id === d)?.typeName ?? "документ"}</Button>)}
                </li>
              );
            })}
          </ul>
        </Card>
      )}

      {!docs.length ? (
        <Card><EmptyState title="Документов пока нет" text="Соискатель ещё не загрузил файлы" /></Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="lg:sticky lg:top-20 lg:h-svh lg:self-start lg:pb-24">
            <Card className="h-96 overflow-hidden sm:h-svh lg:h-full">{doc && <DocumentViewer doc={doc} highlight={field ? { page: field.page, bbox: field.bbox as number[] | null } : null} />}</Card>
          </div>
          <div className="flex min-w-0 flex-col gap-3">
            <div className="flex flex-wrap gap-1.5" role="tablist" aria-label="Документы">
              {docs.map((d) => (
                <Button key={d.id} size="sm" role="tab" aria-selected={d.id === doc?.id} variant={d.id === doc?.id ? "inverse" : "secondary"} onClick={() => setDocId(d.id!)}>
                  {d.attention && <AlertTriangle className="text-warning" />}{d.typeName ?? d.type ?? d.slotType ?? "Документ"}{d.confirmed && " ✓"}
                </Button>
              ))}
            </div>
            {doc && (
              <Card className="p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="font-display text-lg font-semibold">{doc.typeName ?? doc.type ?? "Вид не определён"}</h2>
                  {doc.typeConfidence != null && <Status tone={confidenceTone(doc.typeConfidence)}>вид {percent(doc.typeConfidence)}</Status>}
                  <StageBadge stage={doc.stage} />
                  {doc.expiresOn && <Status tone="neutral"><Timer />до {dateRuFull(doc.expiresOn)}</Status>}
                </div>
                {doc.slotType && doc.type && doc.slotType !== doc.type && <p className="mt-1 text-xs text-muted-foreground">Соискатель загрузил в пункт «{typeTitle(doc.slotType)}»</p>}
                {reason && <p className="mt-2 rounded-md bg-warning-soft px-3 py-2 text-sm text-warning-soft-foreground"><b className="font-medium">{reason.text}</b>{reason.hint ? ` — ${reason.hint}` : ""}</p>}
                {doc.model && <p className="mt-1 text-xs text-muted-foreground">Модель {doc.model}{doc.promptVersion ? ` · промпт ${doc.promptVersion}` : ""}{doc.confirmedBy ? ` · подтвердил ${doc.confirmedBy} ${dateTimeRu(doc.confirmedAt)}` : ""}</p>}
                <div className="mt-3"><DocumentActions appId={id} doc={doc} canAct={canAct && !decided} canReprocess={can(s.roles, "reprocess") && !decided} /></div>
              </Card>
            )}
            <Segmented value={tab} onChange={setTab} label="Раздел" options={[{ value: "fields", label: "Поля", icon: <ListChecks /> }, { value: "history", label: "История", icon: <History /> }]} />
            {tab === "fields" && doc ? (
              <>
                <FieldsPanel appId={id} doc={doc} canAct={canAct && !decided} active={field?.name ?? null} onActive={setField} />
                <p className="text-xs text-muted-foreground">Клавиши: J/K — поле, E — править, Enter — подтвердить документ, N — следующая заявка. Ждёт {waitingRu(app.submittedAt ? (Date.now() - Date.parse(app.submittedAt)) / 1000 : null)}</p>
              </>
            ) : <Card className="p-4"><HistoryTimeline appId={id} /></Card>}
          </div>
        </div>
      )}
      <DecisionDialog appId={id} open={decide} onClose={() => setDecide(false)} />
      {ask && <RequestDialog appId={id} docs={docs} missing={missing} open={ask} onClose={() => setAsk(false)} />}
    </>
  );
};
