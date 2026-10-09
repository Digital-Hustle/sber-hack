import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Send } from "lucide-react";
import { reviewApi, reviewKeys, type DocumentCard } from "@/entities/application";
import { Button, Dialog, Field, Select, Textarea, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

const PRESETS = ["Переснимите документ: снимок размыт", "Нужен разворот целиком, без обрезанных краёв", "Загрузите действующий документ — срок истёк", "Не хватает страницы"];

/** FE-OP-07 / FR-55: запросить у соискателя документ или пересъёмку — придёт в кабинет по SSE. */
export const RequestDialog = ({ appId, docs, missing, open, onClose }: {
  appId: string; docs: DocumentCard[]; missing: { type: string; title: string }[]; open: boolean; onClose: () => void;
}) => {
  const qc = useQueryClient();
  const options = [
    ...missing.map((t) => ({ value: `t:${t.type}`, label: `Загрузить: ${t.title}` })),
    ...docs.map((d) => ({ value: `d:${d.id}`, label: `Переснять: ${d.typeName ?? d.type ?? "документ"}` })),
  ];
  const [target, setTarget] = useState<string>(options[0]?.value ?? "");
  const [reason, setReason] = useState("");
  const m = useMutation({
    mutationFn: () => reviewApi.request(appId, [target.startsWith("d:") ? { documentId: target.slice(2), reason: reason.trim() } : { type: target.slice(2), reason: reason.trim() }]),
    onSuccess: () => {
      toast.success("Запрос отправлен соискателю");
      void qc.invalidateQueries({ queryKey: reviewKeys.card(appId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.history(appId) });
      onClose();
    },
    onError: (e) => toast.error(errorText(e)),
  });
  return (
    <Dialog open={open} onClose={onClose} title="Запросить у соискателя" description="Текст увидит соискатель — пишите, что сделать"
      footer={<Button block size="lg" disabled={!target || !reason.trim() || m.isPending} onClick={() => m.mutate()}><Send />{m.isPending ? "Отправляем…" : "Отправить запрос"}</Button>}>
      <div className="flex flex-col gap-4">
        <Field label="Что нужно"><Select value={target} onChange={setTarget} options={options} /></Field>
        <Field label="Сообщение"><Textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} rows={3} /></Field>
        <div className="flex flex-wrap gap-1.5">
          {PRESETS.map((p) => <Button key={p} size="sm" variant="secondary" onClick={() => setReason(p)}>{p}</Button>)}
        </div>
      </div>
    </Dialog>
  );
};
