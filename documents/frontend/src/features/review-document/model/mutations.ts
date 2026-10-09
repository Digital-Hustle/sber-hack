import { useMutation, useQueryClient } from "@tanstack/react-query";
import { reviewApi, reviewKeys } from "@/entities/application";
import { toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

/** Все действия оператора над документом: после успеха — свежая карточка и очередь. */
const useAct = <V,>(applicationId: string, fn: (v: V) => Promise<unknown>, ok: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      toast.success(ok);
      void qc.invalidateQueries({ queryKey: reviewKeys.card(applicationId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.history(applicationId) });
      void qc.invalidateQueries({ queryKey: reviewKeys.queueAll });
    },
    onError: (e) => toast.error(errorText(e)),
  });
};

/** FE-OP-05: правка поля — значение оператора поверх значения модели, с историей. */
export const useCorrectField = (appId: string) =>
  useAct(appId, (v: { docId: string; name: string; value: string }) => reviewApi.correct(v.docId, [{ name: v.name, value: v.value }]), "Поле исправлено");
export const useConfirmDocument = (appId: string) => useAct(appId, (docId: string) => reviewApi.confirm(docId), "Документ подтверждён");
export const useRetypeDocument = (appId: string) =>
  useAct(appId, (v: { docId: string; type: string }) => reviewApi.retype(v.docId, v.type), "Вид изменён — поля извлекаются заново");
export const useReprocessDocument = (appId: string) => useAct(appId, (docId: string) => reviewApi.reprocess(docId), "Документ отправлен на повторный разбор");
export const useAssign = (appId: string) => useAct(appId, (force: boolean) => reviewApi.assign(appId, force), "Заявка назначена вам");
