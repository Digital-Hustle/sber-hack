export type StaffRole = "OPERATOR" | "SENIOR_OPERATOR" | "AUDITOR";
export type StaffMember = { login: string; firstName: string; lastName: string; role: StaffRole; enabled: boolean; codeConfigured: boolean; createdAt?: string };
/** Выданный код: показываем один раз (QR), нигде не храним. */
export type Enrollment = { login: string; otpauthUri: string; secret: string };

export const STAFF_ROLE_TITLE: Record<StaffRole, string> = { OPERATOR: "Оператор", SENIOR_OPERATOR: "Старший оператор", AUDITOR: "Аудитор" };
