import { useEffect, useSyncExternalStore } from "react";
import { env } from "@/shared/config/env";
import { setAuth } from "@/shared/api";

export type Role = "OPERATOR" | "SENIOR_OPERATOR" | "AUDITOR";
export type Perm = "queue" | "act" | "decide" | "reprocess" | "export" | "reports" | "metrics" | "history" | "staff";

/** Права зеркалят матрицу ролей бэка (docs/ROLES.md). Решает всё равно сервер — здесь только что показывать. */
const PERMS: Record<Role, Perm[]> = {
  OPERATOR: ["queue", "act", "decide", "history", "metrics"],
  SENIOR_OPERATOR: ["queue", "act", "decide", "reprocess", "export", "reports", "metrics", "history", "staff"],
  AUDITOR: ["queue", "history", "metrics"],
};
const LABEL: Record<Role, string> = { OPERATOR: "оператор", SENIOR_OPERATOR: "старший оператор", AUDITOR: "аудитор" };

/** Сессия: access — только в памяти вкладки; refresh — в sessionStorage, чтобы пережить перезагрузку (закрыли вкладку — вышли). */
export type SessionUser = { access_token: string; expiresAt: number; claims: Record<string, unknown> };
type Tokens = { access_token: string; refresh_token: string; expires_in: number };
type State = { user: SessionUser | null; ready: boolean };

const TOKEN_URL = `${env.oidcAuthority}/protocol/openid-connect/token`;
const LOGOUT_URL = `${env.oidcAuthority}/protocol/openid-connect/logout`;
const REFRESH = "documents.refresh";
const LOGINS = "documents.logins";
const REFRESH_AHEAD_MS = 30_000;

let state: State = { user: null, ready: false };
const subs = new Set<() => void>();
const set = (s: Partial<State>) => { state = { ...state, ...s }; subs.forEach((f) => f()); };
let timer: ReturnType<typeof setTimeout> | undefined;

const decode = (jwt: string): Record<string, unknown> => {
  try {
    const p = jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(decodeURIComponent(escape(atob(p)))) as Record<string, unknown>;
  } catch {
    return {};
  }
};

/** Ошибка входа: код ответа Keycloak → понятная причина (тексты — в форме входа). */
export type SignInError = "BAD_CODE" | "LOCKED" | "NOT_CONFIGURED" | "OFFLINE";
export class SignInFailure extends Error {
  constructor(readonly reason: SignInError) { super(reason); }
}

const post = async (url: string, form: Record<string, string>) => {
  let r: Response;
  try {
    r = await fetch(url, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams(form) });
  } catch {
    throw new SignInFailure("OFFLINE");
  }
  if (r.ok) return r.status === 204 ? null : ((await r.json()) as Tokens);
  const body = (await r.json().catch(() => ({}))) as { error_description?: string };
  const d = (body.error_description ?? "").toLowerCase();
  if (r.status >= 500) throw new SignInFailure("NOT_CONFIGURED"); // у учётки нет кода — Keycloak падает на проверке OTP
  if (d.includes("disabled") || d.includes("not fully set up")) throw new SignInFailure("LOCKED");
  throw new SignInFailure("BAD_CODE");
};

const accept = (t: Tokens) => {
  sessionStorage.setItem(REFRESH, t.refresh_token);
  const user: SessionUser = { access_token: t.access_token, expiresAt: Date.now() + t.expires_in * 1000, claims: decode(t.access_token) };
  clearTimeout(timer);
  timer = setTimeout(() => { void refresh(); }, Math.max(t.expires_in * 1000 - REFRESH_AHEAD_MS, 5_000));
  set({ user, ready: true });
  return user;
};
const drop = () => {
  clearTimeout(timer);
  sessionStorage.removeItem(REFRESH);
  set({ user: null, ready: true });
};

let refreshing: Promise<SessionUser | null> | null = null;
const refresh = () => {
  const rt = sessionStorage.getItem(REFRESH);
  if (!rt) { drop(); return Promise.resolve(null); }
  refreshing ??= post(TOKEN_URL, { grant_type: "refresh_token", client_id: env.oidcClientId, refresh_token: rt })
    .then((t) => accept(t!))
    .catch((e) => { if (!(e instanceof SignInFailure && e.reason === "OFFLINE")) drop(); return null; })
    .finally(() => { refreshing = null; });
  return refreshing;
};

/** Логины, которыми входили на этом устройстве (не секрет) — чтобы оставалось ввести только код. */
export const recentLogins = (): string[] => {
  try { return (JSON.parse(localStorage.getItem(LOGINS) ?? "[]") as string[]).slice(0, 4); } catch { return []; }
};
const rememberLogin = (login: string) =>
  localStorage.setItem(LOGINS, JSON.stringify([login, ...recentLogins().filter((l) => l !== login)].slice(0, 4)));
export const forgetLogin = (login: string) => localStorage.setItem(LOGINS, JSON.stringify(recentLogins().filter((l) => l !== login)));

/**
 * FE-UI-03: вход без пароля — логин + 6 цифр из приложения-аутентификатора. Форма своя (как в песочнице),
 * Keycloak проверяет код потоком documents-code-direct (grant_type=password без пароля, параметр otp).
 */
export const signInWithCode = async (login: string, code: string) => {
  const l = login.trim().toLowerCase();
  const t = await post(TOKEN_URL, { grant_type: "password", client_id: env.oidcClientId, username: l, otp: code, scope: "openid" });
  rememberLogin(l);
  return accept(t!);
};

const fresh = async () => {
  const u = state.user;
  if (!u) return null;
  return u.expiresAt - Date.now() > REFRESH_AHEAD_MS / 2 ? u : refresh();
};
const bearer = async () => {
  const u = await fresh();
  return u ? `Bearer ${u.access_token}` : null;
};
const unauthorized = () => { void refresh(); };

export const rolesOf = (u: SessionUser | null): Role[] => {
  if (!u) return [];
  const realm = u.claims.realm_access as { roles?: string[] } | undefined;
  return (realm?.roles ?? []).filter((r): r is Role => r in PERMS);
};

export const can = (roles: Role[], p: Perm) => roles.some((r) => PERMS[r].includes(p));
export const mainRole = (roles: Role[]): Role | null =>
  roles.includes("SENIOR_OPERATOR") ? "SENIOR_OPERATOR" : roles.includes("OPERATOR") ? "OPERATOR" : roles.includes("AUDITOR") ? "AUDITOR" : null;
export const roleLabel = (r: Role | null) => (r ? LABEL[r] : "нет роли");

/**
 * Зона оператора: каждый запрос — с Bearer-токеном. Источник токена ставим синхронно при рендере оболочки —
 * до того, как дочерние страницы начнут запросы (эффекты детей выполняются раньше эффектов родителя).
 */
export const useOperatorAuth = () => {
  setAuth(bearer, unauthorized);
  useEffect(() => { if (!state.ready) void refresh(); }, []);
};

export const useSession = () => {
  const s = useSyncExternalStore((f) => { subs.add(f); return () => subs.delete(f); }, () => state);
  const roles = rolesOf(s.user);
  const c = s.user?.claims ?? {};
  return {
    ready: s.ready,
    user: s.user,
    roles,
    role: mainRole(roles),
    name: ((c.name as string | undefined) || (c.preferred_username as string | undefined) || "Оператор"),
    login: (c.preferred_username as string | undefined) ?? "",
    signOut: async () => {
      const rt = sessionStorage.getItem(REFRESH);
      drop();
      if (rt) await post(LOGOUT_URL, { client_id: env.oidcClientId, refresh_token: rt }).catch(() => null);
    },
  };
};
