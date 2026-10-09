import { api } from "@/shared/api";
import { uid } from "@/shared/lib";
import type { ApplicantApplication, Created, Question, RequiredDocument, Uploaded } from "../model/types";

const base = (id: string) => `/applicant/applications/${id}`;

/** Кабинет соискателя → intake. Ключ заявки подставляет shared/api (Authorization: Applicant <key>). */
export const applicantApi = {
  questionnaire: () => api<{ questions?: Question[] }>("intake", "/applicant/questionnaire"),
  create: (contact: { phone?: string; email?: string }, demo = false) =>
    api<Created>("intake", "/applicant/applications", { method: "POST", body: { consentPd: true, contact, demo }, headers: { "Idempotency-Key": uid() } }),
  resume: (phoneOrEmail: string, resumeCode: string) =>
    api<Created>("intake", "/applicant/resume", { method: "POST", body: { phoneOrEmail, resumeCode } }),
  get: (id: string) => api<ApplicantApplication>("intake", base(id)),
  profile: (id: string, answers: Record<string, unknown>) =>
    api<{ requiredDocuments?: RequiredDocument[] }>("intake", `${base(id)}/profile`, { method: "PUT", body: { answers } }),
  upload: (id: string, file: File, slotType?: string) => {
    const fd = new FormData();
    fd.append("file", file);
    const q = slotType ? `?slotType=${encodeURIComponent(slotType)}` : "";
    return api<Uploaded>("intake", `${base(id)}/documents${q}`, { method: "POST", body: fd, headers: { "Idempotency-Key": uid() } });
  },
  addFile: (id: string, docId: string, file: File, mode: "add" | "replace") => {
    const fd = new FormData();
    fd.append("file", file);
    return api<Uploaded>("intake", `${base(id)}/documents/${docId}/files?mode=${mode}`, { method: "POST", body: fd, headers: { "Idempotency-Key": uid() } });
  },
  remove: (id: string, docId: string) => api<void>("intake", `${base(id)}/documents/${docId}`, { method: "DELETE" }),
  submit: (id: string) => api<{ status?: string; missing?: string[] }>("intake", `${base(id)}/submit`, { method: "POST" }),
};

export const applicantKeys = {
  questionnaire: ["applicant", "questionnaire"] as const,
  application: (id: string) => ["applicant", "application", id] as const,
};
