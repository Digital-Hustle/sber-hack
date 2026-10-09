import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Send } from "lucide-react";
import { applicantApi, applicantKeys } from "@/entities/application";
import { Button, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

/** FE-AP-07: отправить комплект оператору. Полноту решает сервер — «не хватает» приходит в ответе. */
export const SubmitButton = ({ applicationId, ready, titleOf }: { applicationId: string; ready: boolean; titleOf: (t: string) => string }) => {
  const qc = useQueryClient();
  const m = useMutation({
    mutationFn: () => applicantApi.submit(applicationId),
    onSuccess: (r) => {
      void qc.invalidateQueries({ queryKey: applicantKeys.application(applicationId) });
      if (r?.missing?.length) toast.info(`Отправлено. Не хватает: ${r.missing.map(titleOf).join(", ")}`);
      else toast.success("Комплект отправлен оператору");
    },
    onError: (e) => toast.error(errorText(e)),
  });
  return (
    <Button size="lg" block variant={ready ? "brand" : "primary"} disabled={m.isPending} onClick={() => m.mutate()}>
      <Send />{m.isPending ? "Отправляем…" : ready ? "Отправить комплект" : "Отправить то, что есть"}
    </Button>
  );
};
