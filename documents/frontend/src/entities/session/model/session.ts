import { useEffect, useSyncExternalStore } from "react";
import { UserManager, WebStorageStateStore, InMemoryWebStorage, type User } from "oidc-client-ts";
import { env } from "@/shared/config/env";
import { setAuth } from "@/shared/api";
import { routes } from "@/shared/const/router";

export type Role = "OPERATOR" | "SENIOR_OPERATOR" | "AUDITOR";
export type Perm = "queue" | "act" | "decide" | "reprocess" | "export" | "reports" | "metrics" | "history";

/** Права зеркалят матрицу ролей бэка (docs/ROLES.md). Решает всё равно сервер — здесь только что показывать. */
const PERMS: Record<Role, Perm[]> = {
  OPERATOR: ["queue", "act", "decide", "history", "metrics"],
  SENIOR_OPERATOR: ["queue", "act", "decide", "reprocess", "export", "reports", "metrics", "history"],
  AUDITOR: ["queue", "history", "metrics"],
};
const LABEL: Record<Role, string> = { OPERATOR: "оператор", SENIOR_OPERATOR: "старший оператор", AUDITOR: "аудитор" };

/**
 * OIDC Authorization Code + PKCE (Keycloak). Токены — только в памяти вкладки (InMemoryWebStorage):
 * не в localStorage, после закрытия вкладки ПДн и токены не остаются. Обновление — refresh_token.
 */
export const userManager = new UserManager({
  authority: env.oidcAuthority,
  client_id: env.oidcClientId,
  redirect_uri: `${location.origin}${routes.operatorCallback}`,
  post_logout_redirect_uri: `${location.origin}/`,
  response_type: "code",
  scope: "openid profile email",
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  stateStore: new WebStorageStateStore({ store: sessionStorage }),
});

type State = { user: User | null; ready: boolean };
let state: State = { user: null, ready: false };
const subs = new Set<() => void>();
const set = (s: Partial<State>) => { state = { ...state, ...s }; subs.forEach((f) => f()); };

userManager.events.addUserLoaded((u) => set({ user: u }));
userManager.events.addUserUnloaded(() => set({ user: null }));
userManager.events.addSilentRenewError(() => set({ user: null }));

const decode = (jwt: string): Record<string, unknown> => {
  try {
    const p = jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(decodeURIComponent(escape(atob(p)))) as Record<string, unknown>;
  } catch {
    return {};
  }
};

export const rolesOf = (u: User | null): Role[] => {
  if (!u) return [];
  const realm = decode(u.access_token).realm_access as { roles?: string[] } | undefined;
  return (realm?.roles ?? []).filter((r): r is Role => r in PERMS);
};

export const can = (roles: Role[], p: Perm) => roles.some((r) => PERMS[r].includes(p));
export const mainRole = (roles: Role[]): Role | null =>
  roles.includes("SENIOR_OPERATOR") ? "SENIOR_OPERATOR" : roles.includes("OPERATOR") ? "OPERATOR" : roles.includes("AUDITOR") ? "AUDITOR" : null;
export const roleLabel = (r: Role | null) => (r ? LABEL[r] : "нет роли");

/** Зона оператора: каждый запрос — с Bearer-токеном; 401 → сессия сброшена, RequireAuth уведёт на вход. */
export const useOperatorAuth = () => {
  useEffect(() => {
    setAuth(() => (state.user && !state.user.expired ? `Bearer ${state.user.access_token}` : null), () => {
      if (state.user?.expired) void userManager.removeUser();
    });
    if (!state.ready) {
      userManager.getUser().then((u) => set({ user: u && !u.expired ? u : null, ready: true })).catch(() => set({ ready: true }));
    }
  }, []);
};

export const useSession = () => {
  const s = useSyncExternalStore((f) => { subs.add(f); return () => subs.delete(f); }, () => state);
  const roles = rolesOf(s.user);
  const profile = s.user?.profile;
  return {
    ready: s.ready,
    user: s.user,
    roles,
    role: mainRole(roles),
    name: (profile?.name ?? profile?.preferred_username ?? "Оператор") as string,
    login: (profile?.preferred_username ?? "") as string,
    signIn: (next = location.pathname + location.search) => userManager.signinRedirect({ state: { next } }),
    signOut: async () => {
      const u = state.user;
      await userManager.removeUser();
      if (u?.id_token) await userManager.signoutRedirect({ id_token_hint: u.id_token });
    },
  };
};

/** Возврат из Keycloak: обмен кода на токены, затем — туда, откуда ушли на вход. */
export const completeSignIn = async (): Promise<string> => {
  const u = await userManager.signinRedirectCallback();
  set({ user: u, ready: true });
  const next = (u.state as { next?: string } | undefined)?.next;
  return next && next.startsWith(routes.operator) && next !== routes.operatorCallback ? next : routes.operator;
};
