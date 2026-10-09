import { api } from "@/shared/api";
import type { Enrollment, StaffMember, StaffRole } from "../model/types";

/** FE-UI-03: сотрудники (только старший) → review /staff. */
export const staffApi = {
  list: () => api<{ items?: StaffMember[] }>("review", "/staff").then((r) => r.items ?? []),
  create: (b: { login: string; firstName: string; lastName: string; role: StaffRole }) =>
    api<Enrollment>("review", "/staff", { method: "POST", body: b }),
  reissue: (login: string) => api<Enrollment>("review", `/staff/${encodeURIComponent(login)}/code`, { method: "POST" }),
  setEnabled: (login: string, enabled: boolean) =>
    api<void>("review", `/staff/${encodeURIComponent(login)}/enabled`, { method: "PUT", body: { enabled } }),
};
export const staffKeys = { all: ["staff"] as const };
