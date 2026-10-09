import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { applicantApi, applicantKeys } from "@/entities/application";
import { ApiError, errorText } from "@/shared/api";
import { shrinkImage } from "@/shared/lib";
import { env } from "@/shared/config/env";

export type UploadError = { text: string; hint?: string };
type Vars = { file: File; slotType?: string; docId?: string; mode?: "add" | "replace" };

/**
 * FE-AP-03/06: загрузка в пункт, дозагрузка страницы, замена. Отказ intake — не toast, а состояние пункта
 * (текст и подсказка из problem+json). Клиентская проверка размера — только подсказка, решает сервер.
 */
export const useUpload = (applicationId: string) => {
  const qc = useQueryClient();
  const [errors, setErrors] = useState<Record<string, UploadError | undefined>>({});
  const [warnings, setWarnings] = useState<Record<string, string[] | undefined>>({});
  const key = (v: Vars) => v.docId ?? v.slotType ?? "extra";
  const m = useMutation({
    mutationFn: async (v: Vars) => {
      const file = await shrinkImage(v.file);
      return v.docId ? applicantApi.addFile(applicationId, v.docId, file, v.mode ?? "add") : applicantApi.upload(applicationId, file, v.slotType);
    },
    onMutate: (v) => setErrors((s) => ({ ...s, [key(v)]: undefined })),
    onSuccess: (r, v) => {
      setWarnings((s) => ({ ...s, [key(v)]: r?.warnings?.length ? r.warnings : undefined }));
      void qc.invalidateQueries({ queryKey: applicantKeys.application(applicationId) });
    },
    onError: (e, v) => setErrors((s) => ({ ...s, [key(v)]: { text: errorText(e), hint: e instanceof ApiError ? e.hint : "Проверьте связь и повторите" } })),
  });
  return {
    upload: (v: Vars) => m.mutate(v),
    pendingFor: m.isPending ? key(m.variables) : null,
    errorFor: (k: string) => errors[k],
    warningsFor: (k: string) => warnings[k],
    clearError: (k: string) => setErrors((s) => ({ ...s, [k]: undefined })),
    tooBigHint: (f: File) => (f.size > env.uploadMaxMb * 1024 * 1024 && !f.type.startsWith("image/") ? `Файл больше ${env.uploadMaxMb} МБ — сервер может его не принять` : null),
  };
};

/** FE-AP-06: удалить документ из заявки (до решения оператора — проверяет сервер). */
export const useRemoveDocument = (applicationId: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (docId: string) => applicantApi.remove(applicationId, docId),
    onSettled: () => qc.invalidateQueries({ queryKey: applicantKeys.application(applicationId) }),
  });
};
