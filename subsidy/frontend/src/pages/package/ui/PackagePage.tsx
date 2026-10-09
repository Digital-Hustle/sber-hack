import { useCallback } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, FileText, History } from "lucide-react";
import { packageApi, packageKeys, PackageStatusBadge, reasonText, StageBadge, type DocumentResult, type PackageCard } from "@/entities/package";
import { protocolKeys } from "@/entities/protocol";
import { can, useSession } from "@/entities/session";
import { AssistantChat } from "@/features/ask-assistant";
import { DecideButton } from "@/features/decide-package";
import { ExportButtons } from "@/features/export-protocol";
import { FileActions } from "@/features/manage-file";
import { UploadDropzone } from "@/features/upload-files";
import { DocumentViewer } from "@/widgets/document-viewer";
import { ProtocolPanel } from "@/widgets/protocol-panel";
import { errorText, useEventStream } from "@/shared/api";
import { Button, Card, CardContent, CardHeader, CardTitle, EmptyState, PageHeader, RowsSkeleton, Segmented, Status } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { cn } from "@/shared/lib";

type Tab = "protocol" | "documents" | "assistant";
const EVENTS = ["file.stage", "document.parsed", "package.checked"];
const DECISION = { APPROVE: "Одобрено", REJECT: "Отказано", RETURN: "Возвращено на доработку" } as const;
const PERCENT = 100;

const Files = ({ card, docs, typeName, selected, onSelect, canEdit, canReprocess }: {
  card: PackageCard; docs: Map<string, DocumentResult>; typeName: (t: string | null) => string; selected: string | null;
  onSelect: (id: string) => void; canEdit: boolean; canReprocess: boolean;
}) => (
  <Card>
    <CardHeader><CardTitle>Файлы · {card.files.length}</CardTitle></CardHeader>
    <CardContent className="flex flex-col gap-3">
      {!card.files.length && <p className="text-sm text-muted-foreground">Загрузите сканы, PDF или ZIP с документами организации</p>}
      <ul className="flex flex-col gap-3">
        {card.files.map((f) => (
          <li key={f.fileId} className="flex flex-col gap-1.5">
            <div className="flex items-center gap-2">
              <FileText className="size-4 shrink-0 text-muted-foreground" />
              <span className="min-w-0 flex-1 truncate text-sm font-medium" title={f.name}>{f.name}{f.version > 1 ? ` · v${f.version}` : ""}</span>
              <FileActions packageId={card.packageId} fileId={f.fileId} name={f.name} canEdit={canEdit} canReprocess={canReprocess} />
            </div>
            <div className="flex flex-wrap gap-1"><StageBadge stage={f.stage} />{reasonText(f.reasonCode) && <Status tone="warning">{reasonText(f.reasonCode)}</Status>}</div>
            {f.documents.map((d) => {
              const r = docs.get(d.documentId);
              return (
                <button key={d.documentId} type="button" onClick={() => onSelect(d.documentId)}
                  className={cn("flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm outline-none transition-colors duration-fast hover:bg-surface focus-visible:ring-2 focus-visible:ring-ring", selected === d.documentId && "bg-surface")}>
                  <span className="min-w-0 flex-1 truncate">{typeName(r?.type ?? d.type)} <span className="text-muted-foreground">стр. {d.fromPage}–{d.toPage}</span></span>
                  {d.typeConfidence !== null && <span className="text-xs tabular-nums text-muted-foreground">{Math.round(d.typeConfidence * PERCENT)}%</span>}
                  <StageBadge stage={r?.stage ?? d.stage} />
                </button>
              );
            })}
          </li>
        ))}
      </ul>
    </CardContent>
  </Card>
);

/** FE-PK-03..07, FE-SP-03..06, FE-SP-10: карточка пакета — файлы и документы по SSE, протокол, поля, решение, помощник. */
export const PackagePage = () => {
  const { id = "" } = useParams();
  const [sp, setSp] = useSearchParams();
  const { roles } = useSession();
  const qc = useQueryClient();
  const tab = (sp.get("tab") as Tab | null) ?? "protocol";
  const selected = sp.get("doc");
  const setParam = useCallback((k: string, v: string | null) => setSp((p) => { const n = new URLSearchParams(p); if (v) n.set(k, v); else n.delete(k); return n; }, { replace: true }), [setSp]);

  const card = useQuery({ queryKey: packageKeys.card(id), queryFn: () => packageApi.card(id) });
  const documents = useQuery({ queryKey: packageKeys.documents(id), queryFn: () => packageApi.documents(id) });
  const types = useQuery({ queryKey: packageKeys.types, queryFn: packageApi.types, staleTime: Infinity });

  useEventStream("package", "/stream/ticket", `/stream?packageId=${id}`, EVENTS, (name) => {
    void qc.invalidateQueries({ queryKey: packageKeys.card(id) });
    if (name !== "file.stage") void qc.invalidateQueries({ queryKey: packageKeys.documents(id) });
    if (name === "package.checked" || name === "reconnect") {
      void qc.invalidateQueries({ queryKey: protocolKeys.latest(id) });
      void qc.invalidateQueries({ queryKey: protocolKeys.versions(id) });
    }
  });

  if (card.isPending) return <RowsSkeleton rows={6} />;
  if (card.isError) return <EmptyState title="Пакет не загрузился" text={errorText(card.error)} action={<Button variant="secondary" onClick={() => card.refetch()}>Повторить</Button>} />;
  const c = card.data;
  const docs = new Map((documents.data?.items ?? []).map((d) => [d.documentId, d]));
  const names = new Map((types.data?.items ?? []).map((t) => [t.code, t.name]));
  const typeName = (t: string | null) => (t ? names.get(t) ?? t : "Вид не определён");
  const doc = selected ? docs.get(selected) : undefined;
  const checked = c.status === "CHECKED" || c.status === "DECIDED";
  const canEdit = can(roles, "work") && c.status !== "DECIDED";
  const openDoc = (documentId: string) => setSp((p) => { const n = new URLSearchParams(p); n.set("doc", documentId); n.set("tab", "documents"); return n; }, { replace: true });

  return (
    <>
      <Link to={routes.packages} className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Пакеты</Link>
      <PageHeader title={c.org.name || `ИНН ${c.org.inn}`}
        sub={<span className="flex flex-wrap items-center gap-2">ИНН {c.org.inn} · {c.program} <PackageStatusBadge status={c.status} />
          <Link to={routes.org(c.org.inn)} className="inline-flex items-center gap-1 text-sm underline-offset-2 hover:underline"><History className="size-4" />история</Link></span>}
        actions={<div className="flex flex-wrap gap-2">{checked && <ExportButtons packageId={id} />}{c.status === "CHECKED" && can(roles, "decide") && <DecideButton packageId={id} />}</div>} />
      {c.decision && (
        <Card className="mb-4"><CardContent className="flex flex-wrap items-center gap-2 text-sm">
          <Status tone={c.decision.decision === "APPROVE" ? "success" : c.decision.decision === "REJECT" ? "danger" : "warning"} dot>{DECISION[c.decision.decision]}</Status>
          <span className="text-muted-foreground">{c.decision.decidedBy} · {new Date(c.decision.decidedAt).toLocaleString("ru-RU")}</span>
          {c.decision.comment && <p className="basis-full whitespace-pre-wrap">{c.decision.comment}</p>}
        </CardContent></Card>
      )}
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="flex flex-col gap-4">
          {canEdit && <UploadDropzone packageId={id} />}
          <Files card={c} docs={docs} typeName={typeName} selected={selected} onSelect={openDoc} canEdit={canEdit} canReprocess={can(roles, "reprocess")} />
        </div>
        <div className="flex flex-col gap-4 lg:col-span-2">
          <Segmented label="Раздел" value={tab} onChange={(v) => setParam("tab", v)}
            options={[{ value: "protocol", label: "Протокол" }, { value: "documents", label: "Документ" }, { value: "assistant", label: "Помощник" }]} />
          {tab === "protocol" && <ProtocolPanel packageId={id} onEvidence={(d) => openDoc(d)} />}
          {tab === "documents" && (doc
            ? <DocumentViewer packageId={id} doc={doc} typeName={typeName(doc.type)} />
            : <Card><EmptyState icon={<FileText />} title="Выберите документ" text="Слева — документы, найденные в файлах пакета" className="py-16" /></Card>)}
          {tab === "assistant" && <AssistantChat packageId={id} />}
        </div>
      </div>
    </>
  );
};
