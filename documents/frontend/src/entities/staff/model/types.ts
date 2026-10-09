export type StaffRole = "OPERATOR" | "SENIOR_OPERATOR" | "AUDITOR";
export type StaffMember = { login: string; email: string; firstName: string; lastName: string; role: StaffRole; enabled: boolean; createdAt?: string };

export const STAFF_ROLE_TITLE: Record<StaffRole, string> = { OPERATOR: "Оператор", SENIOR_OPERATOR: "Старший оператор", AUDITOR: "Аудитор" };
