import { create } from "zustand";
import { clearStaffSession, getStaffUser, onStaffChange, renewStaff, type Role, type StaffUser } from "@/shared/api";

export type Perm = "dashboard" | "people" | "journal" | "shifts" | "analytics" | "assistant" | "terminals" | "settings" | "reviewManual" | "access" | "objects" | "manageObjects";

export const PERMS: { id: Perm; label: string }[] = [
  { id: "dashboard", label: "Обстановка" }, { id: "people", label: "Люди" }, { id: "journal", label: "Журнал" }, { id: "shifts", label: "Смены" },
  { id: "analytics", label: "Аналитика" }, { id: "assistant", label: "Помощник" }, { id: "objects", label: "Объекты" }, { id: "terminals", label: "Терминалы" }, { id: "settings", label: "Настройки" }, { id: "reviewManual", label: "Проверка ручных" }, { id: "access", label: "Выдача ролей" }, { id: "manageObjects", label: "Полное управление" },
];

export const ROLES: { id: Role; label: string; text: string }[] = [
  { id: "ADMIN", label: "Администратор", text: "Все разделы, роли, настройки и полное управление объектами, зонами, проходными и людьми" },
  { id: "SECURITY_OFFICER", label: "Служба безопасности", text: "Журнал, инциденты, терминалы и правила прохода" },
  { id: "MANAGER", label: "Руководитель участка", text: "Люди, смены, табель и помощник" },
  { id: "INSTALLER", label: "Инженер терминалов", text: "Подключение и обслуживание киосков, без доступа к людям" },
  { id: "GUARD", label: "Охранник", text: "Обстановка и журнал проходов" },
];

const MATRIX: Record<Role, Perm[]> = {
  ADMIN: PERMS.map((p) => p.id),
  SECURITY_OFFICER: ["dashboard", "people", "journal", "analytics", "objects", "terminals", "settings", "reviewManual"],
  MANAGER: ["dashboard", "people", "journal", "shifts", "analytics", "assistant", "objects"],
  INSTALLER: ["dashboard", "terminals"],
  GUARD: ["dashboard", "journal", "objects"],
};

/** Проверка права роли. Только для интерфейса: те же права проверяет сервер (FR-60). */
export const can = (role: Role, perm: Perm) => MATRIX[role].includes(perm);
export const roleLabel = (role: Role) => ROLES.find((r) => r.id === role)?.label ?? role;

type SessionState = {
  userId: string; role: Role;
  /** Вошёл ли сотрудник (access-токен живёт только в памяти, refresh — httpOnly-cookie). */
  user: StaffUser | null;
  /** Попытка восстановить сессию при старте завершена: до этого кабинет не уводит на вход. */
  ready: boolean;
  /** Совместимость со старым кодом страниц: есть ли живая сессия. */
  token: string | null; expiresAt: number;
  start: (p: { userId: string; role: Role; token: string; expiresAt: number }) => void;
  signOut: () => void;
};

const fromAuth = () => {
  const u = getStaffUser();
  return { user: u, userId: u?.id ?? "", role: (u?.role ?? "GUARD") as Role, token: u ? "live" : null };
};

/** Пользователь кабинета. Вход — только по коду из письма (страница /login); источник правды — remote/auth. */
export const useSession = create<SessionState>()((set) => ({
  ...fromAuth(), ready: false, expiresAt: 0,
  start: (p) => set({ ...fromAuth(), expiresAt: p.expiresAt, ready: true }),
  signOut: () => { clearStaffSession(); set({ ...fromAuth(), expiresAt: 0 }); },
}));

onStaffChange(() => useSession.setState(fromAuth()));

let restoring: Promise<void> | null = null;
/** Перезагрузка страницы: пробуем обновить сессию по refresh-cookie. Не вышло — просто экран входа. */
export const restoreSession = () => {
  restoring ??= (getStaffUser() ? Promise.resolve(true) : renewStaff()).then(() => { useSession.setState({ ...fromAuth(), ready: true }); });
  return restoring;
};
