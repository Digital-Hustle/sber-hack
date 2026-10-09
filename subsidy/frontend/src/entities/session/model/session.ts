import { useEffect, useSyncExternalStore } from "react";
import { UserManager, WebStorageStateStore, InMemoryWebStorage, type User } from "oidc-client-ts";
import { env } from "@/shared/config/env";
import { setAuth } from "@/shared/api";
import { routes } from "@/shared/const/router";

export type Role = "SPECIALIST" | "SENIOR_SPECIALIST" | "AUDITOR";
export type Perm = "work" | "decide" | "programs" | "reprocess" | "stats";

/** Права зеркалят матрицу ролей бэка (docs/API.md §Аутентификация). Решает сервер — здесь только что показывать. */
const PERMS: Record<Role, Perm[]> = {
  SPECIALIST: ["work", "stats"],
  SENIOR_SPECIALIST: ["work", "decide", "programs", "reprocess", "stats"],
  AUDITOR: ["stats"],
};
const LABEL: Record<Role, string> = { SPECIALIST: "специалист", SENIOR_SPECIALIST: "старший специалист", AUDITOR: "аудитор" };

/**
 * OIDC Authorization Code + PKCE (Keycloak). Токены — только в памяти вкладки (InMemoryWebStorage),
 * не в localStorage (RULES §Избегай). Обновление — refresh_token.
 */
export const userManager = new UserManager({
  authority: env.oidcAuthority,
  client_id: env.oidcClientId,
  redirect_uri: `${location.origin}${routes.callback}`,
  post_logout_redirect_uri: `${location.origin}${routes.specialist}`,
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

/** Роли — из claim `roles` access-токена (маппер realm subsidy). */
export const rolesOf = (u: User | null): Role[] => {
  if (!u) return [];
  const roles = decode(u.access_token).roles;
  return (Array.isArray(roles) ? roles : []).filter((r): r is Role => typeof r === "string" && r in PERMS);
};

export const can = (roles: Role[], p: Perm) => roles.some((r) => PERMS[r].includes(p));
export const mainRole = (roles: Role[]): Role | null =>
  roles.includes("SENIOR_SPECIALIST") ? "SENIOR_SPECIALIST" : roles.includes("SPECIALIST") ? "SPECIALIST" : roles.includes("AUDITOR") ? "AUDITOR" : null;
export const roleLabel = (r: Role | null) => (r ? LABEL[r] : "нет роли");

/** Каждый запрос — с Bearer-токеном; 401 → сессия сброшена, RequireAuth уведёт на вход. */
export const useSpecialistAuth = () => {
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
    name: (profile?.name ?? profile?.preferred_username ?? "Специалист") as string,
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
  return next && next.startsWith(routes.specialist) && next !== routes.callback ? next : routes.packages;
};
