import { api } from "@/shared/api";
import type { StaffMember, StaffRole } from "../model/types";

/** FE-UI-04: сотрудники (только старший) → review /staff. Приглашение — по почте, код входа придёт письмом. */
export const staffApi = {
  list: () => api<{ items?: StaffMember[] }>("review", "/staff").then((r) => r.items ?? []),
  invite: (b: { email: string; firstName: string; lastName: string; role: StaffRole }) =>
    api<StaffMember>("review", "/staff", { method: "POST", body: b }),
  setEnabled: (login: string, enabled: boolean) =>
    api<void>("review", `/staff/${encodeURIComponent(login)}/enabled`, { method: "PUT", body: { enabled } }),
};
export const staffKeys = { all: ["staff"] as const };
