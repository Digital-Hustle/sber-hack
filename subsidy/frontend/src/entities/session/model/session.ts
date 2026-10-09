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

/**
 * Тихий вход при открытии вкладки: токены живут только в памяти, но сессия Keycloak (cookie) жива —
 * один редирект с prompt=none возвращает токены без формы входа. Не вышло (login_required) — показываем вход.
 * Неудачная попытка запоминается до следующего успешного входа, чтобы не зациклиться.
 */
const SSO_TRIED = "subsidy.sso";
const NEXT = "subsidy.next";
const trySilent = () => {
  if (sessionStorage.getItem(SSO_TRIED)) return false;
  sessionStorage.setItem(SSO_TRIED, "1");
  sessionStorage.setItem(NEXT, location.pathname + location.search);
  void userManager.signinRedirect({ prompt: "none", state: { next: location.pathname + location.search } });
  return true;
};

/** Токен, которому осталось жить меньше этого, обновляем до запроса (фоновые вкладки пропускают таймер автообновления). */
const REFRESH_AHEAD_S = 20;
let refreshing: Promise<User | null> | null = null;
const fresh = async (): Promise<User | null> => {
  const u = state.user;
  if (!u) return null;
  if (!u.expired && (u.expires_in ?? 0) > REFRESH_AHEAD_S) return u;
  refreshing ??= userManager.signinSilent().catch(() => null).finally(() => { refreshing = null; });
  return refreshing;
};
const bearer = async () => {
  const u = await fresh();
  return u && !u.expired ? `Bearer ${u.access_token}` : null;
};
const unauthorized = () => { if (state.user?.expired) void userManager.removeUser(); };

/**
 * Каждый запрос — с Bearer-токеном; 401 → сессия сброшена, оболочка покажет вход.
 * Источник токена ставим синхронно при рендере оболочки — до того, как дочерние страницы начнут запросы
 * (эффекты детей выполняются раньше эффектов родителя, иначе первые запросы уходили без токена и получали 401).
 */
export const useSpecialistAuth = () => {
  setAuth(bearer, unauthorized);
  useEffect(() => {
    if (!state.ready) {
      userManager.getUser()
        .then((u) => (u && !u.expired ? set({ user: u, ready: true }) : trySilent() || set({ user: null, ready: true })))
        .catch(() => set({ ready: true }));
    }
  }, []);
};

const signedIn = (u: User) => {
  sessionStorage.removeItem(SSO_TRIED); // вошли — при следующей перезагрузке снова можно тихо
  set({ user: u, ready: true });
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
  let u: User;
  try {
    u = await userManager.signinRedirectCallback();
  } catch (e) {
    // тихий вход не удался (нет сессии Keycloak) — это не ошибка: обратно, там покажется форма входа
    if ((e as { error?: string }).error === "login_required") {
      set({ user: null, ready: true });
      return sessionStorage.getItem(NEXT) ?? routes.packages;
    }
    throw e;
  }
  signedIn(u);
  const next = (u.state as { next?: string } | undefined)?.next;
  return next && next.startsWith(routes.specialist) && next !== routes.callback ? next : routes.packages;
};
