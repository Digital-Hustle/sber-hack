import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Undo2 } from "lucide-react";
import { reviewApi, reviewKeys } from "@/entities/application";
import { Button, Dialog, Field, Segmented, Textarea, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

type D = "ACCEPTED" | "RETURNED";

/** FE-OP-05: решение по заявке. Вернуть — только с комментарием (его увидит соискатель). */
export const DecisionDialog = ({ appId, open, onClose }: { appId: string; open: boolean; onClose: () => void }) => {
  const qc = useQueryClient();
  const [d, setD] = useState<D>("ACCEPTED");
  const [comment, setComment] = useState("");
  const m = useMutation({
    mutationFn: () => reviewApi.decide(appId, d, comment.trim() || undefined),
    onSuccess: () => {
      toast.success(d === "ACCEPTED" ? "Заявка принята" : "Заявка возвращена соискателю");
      void qc.invalidateQueries({ queryKey: reviewKeys.card(appId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.queueAll });
      onClose();
    },
    onError: (e) => toast.error(errorText(e)),
  });
  const needComment = d === "RETURNED" && !comment.trim();
  return (
    <Dialog open={open} onClose={onClose} title="Решение по заявке" description="Соискатель увидит решение и комментарий в кабинете"
      footer={<Button block size="lg" variant={d === "ACCEPTED" ? "primary" : "danger"} disabled={needComment || m.isPending} onClick={() => m.mutate()}>
        {d === "ACCEPTED" ? <CheckCircle2 /> : <Undo2 />}{m.isPending ? "Сохраняем…" : d === "ACCEPTED" ? "Принять заявку" : "Вернуть на доработку"}</Button>}>
      <div className="flex flex-col gap-4">
        <Segmented block value={d} onChange={setD} label="Решение" options={[{ value: "ACCEPTED", label: "Принять" }, { value: "RETURNED", label: "Вернуть" }]} />
        <Field label={d === "RETURNED" ? "Что исправить (обязательно)" : "Комментарий"}>
          <Textarea value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} rows={4} />
        </Field>
      </div>
    </Dialog>
  );
};
