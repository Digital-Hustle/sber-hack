import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { motion } from "motion/react";
import { AlertTriangle, CheckCircle2, Eye, Pencil, XCircle, Bot, UserPen } from "lucide-react";
import { reviewApi, type Check, type DocumentCard, type FieldCard } from "@/entities/application";
import { checkLabel, confidenceTone, percent } from "@/entities/document";
import { FieldEditor } from "@/features/review-document";
import { Button, EmptyState, Status, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { cn } from "@/shared/lib";
import { fadeUp, stagger } from "@/shared/config/motion";

const RevealButton = ({ appId, docId, field }: { appId: string; docId: string; field: string }) => {
  const [value, setValue] = useState<string | null>(null);
  const m = useMutation({ mutationFn: () => reviewApi.reveal(appId, docId, field), onSuccess: (r) => setValue(r?.value ?? ""), onError: (e) => toast.error(errorText(e)) });
  if (value !== null) return <span className="font-mono text-sm">{value}</span>;
  return <Button size="sm" variant="quiet" disabled={m.isPending} onClick={(e) => { e.stopPropagation(); m.mutate(); }} title="Показ полного номера пишется в журнал"><Eye />Показать</Button>;
};

/**
 * FE-OP-04: поля документа — «перепроверить» сверху (порядок задаёт сервер), уверенность, проверки, правки оператора.
 * Клик по полю → подсветка bbox в просмотрщике. Клавиши: J/K — следующее/предыдущее поле, E — править.
 * FR-54: переключатель «только требующие проверки» скрывает поля, которые не нужно перепроверять.
 */
export const FieldsPanel = ({ appId, doc, canAct, active, onActive }: {
  appId: string; doc: DocumentCard; canAct: boolean; active: string | null; onActive: (f: FieldCard | null) => void;
}) => {
  const all = useMemo(() => doc.fields ?? [], [doc.fields]);
  const [onlyAttention, setOnlyAttention] = useState(false);
  const attentionCount = all.filter((f) => f.attention).length;
  const fields = useMemo(() => (onlyAttention ? all.filter((f) => f.attention) : all), [all, onlyAttention]);
  const [editing, setEditing] = useState<string | null>(null);
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLElement && e.target.closest("input, textarea, [role=dialog]")) return;
      const i = fields.findIndex((f) => f.name === active);
      if (e.key === "j" || e.key === "k") {
        const next = fields[Math.max(0, Math.min(fields.length - 1, i + (e.key === "j" ? 1 : -1)))];
        if (next) { onActive(next); e.preventDefault(); }
      } else if (e.key === "e" && canAct && active) {
        setEditing(active); e.preventDefault();
      }
    };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  }, [fields, active, canAct, onActive]);

  if (!all.length) return <EmptyState title="Полей пока нет" text="Документ ещё разбирается или не прочитан — см. стадию и причину выше" />;
  return (
    <div className="flex flex-col gap-2">
    {attentionCount > 0 && attentionCount < all.length && (
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>{onlyAttention ? `Показаны ${attentionCount} из ${all.length}` : `Требуют проверки: ${attentionCount} из ${all.length}`}</span>
        <Button size="sm" variant="quiet" aria-pressed={onlyAttention} onClick={() => setOnlyAttention((v) => !v)}>
          {onlyAttention ? "Показать все поля" : "Только требующие проверки"}
        </Button>
      </div>
    )}
    <motion.ul variants={stagger(0.02)} initial="hidden" animate="show" className="flex flex-col gap-1.5">
      {fields.map((f) => {
        const checks = (f.checks as Check[] | undefined) ?? [];
        const failed = checks.filter((c) => !c.ok);
        const on = active === f.name;
        return (
          <motion.li key={f.name} variants={fadeUp} onClick={() => onActive(f)}
            className={cn("cursor-pointer rounded-lg border p-3 transition-colors duration-fast",
              on ? "border-primary bg-accent/60" : f.attention ? "border-warning-border bg-warning-soft/40 hover:bg-warning-soft/60" : "border-border bg-card hover:bg-muted")}>
            <div className="flex items-start gap-2">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                  {f.attention && <AlertTriangle className="size-3.5 text-warning" />}
                  <span className="font-medium">{f.title ?? f.name}</span>
                  {f.corrected ? <Status tone="info"><UserPen />исправлено {f.correctedBy ?? ""}</Status> : <Status tone={confidenceTone(f.confidence)}><Bot />{percent(f.confidence)}</Status>}
                </div>
                {editing === f.name ? (
                  <div className="mt-1.5" onClick={(e) => e.stopPropagation()}>
                    <FieldEditor appId={appId} docId={doc.id!} name={f.name!} value={f.value ?? ""} onDone={() => setEditing(null)} />
                  </div>
                ) : (
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <span className={cn("break-words text-base font-medium", !f.value && "text-subtle-foreground")}>{f.value || "— не найдено"}</span>
                    {f.masked && canAct && <RevealButton appId={appId} docId={doc.id!} field={f.name!} />}
                  </div>
                )}
                {f.corrected && f.modelValue !== undefined && f.modelValue !== f.value && <p className="mt-0.5 text-xs text-muted-foreground line-through">модель: {f.modelValue || "—"}</p>}
                {!f.value && f.reason && <p className="mt-0.5 text-xs text-muted-foreground">{f.reason}</p>}
                {checks.length > 0 && (
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    {failed.map((c) => <Status key={c.code} tone="danger"><XCircle />{checkLabel(c.code)}</Status>)}
                    {checks.filter((c) => c.ok).map((c) => <Status key={c.code} tone="success"><CheckCircle2 />{checkLabel(c.code)}</Status>)}
                  </div>
                )}
              </div>
              {canAct && editing !== f.name && (
                <Button size="icon-sm" variant="quiet" aria-label={`Править: ${f.title ?? f.name}`} onClick={(e) => { e.stopPropagation(); onActive(f); setEditing(f.name!); }}><Pencil /></Button>
              )}
            </div>
          </motion.li>
        );
      })}
    </motion.ul>
    </div>
  );
};
