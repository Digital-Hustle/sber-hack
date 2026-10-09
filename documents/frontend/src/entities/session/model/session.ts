import { useEffect, useSyncExternalStore } from "react";
import { OidcClient, UserManager, WebStorageStateStore, InMemoryWebStorage, type User } from "oidc-client-ts";
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

/**
 * Тихий вход при открытии вкладки: токены живут только в памяти, но сессия Keycloak (cookie) жива —
 * один редирект с prompt=none возвращает токены без формы входа. Не вышло (login_required) — показываем вход.
 * Неудачная попытка запоминается до следующего успешного входа, чтобы не зациклиться.
 */
const SSO_TRIED = "documents.sso";
const NEXT = "documents.next";
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
 * Зона оператора: каждый запрос — с Bearer-токеном; 401 → сессия сброшена, RequireAuth уведёт на вход.
 * Источник токена ставим синхронно при рендере оболочки — до того, как дочерние страницы начнут запросы
 * (эффекты детей выполняются раньше эффектов родителя, иначе первые запросы уходили без токена).
 */
export const useOperatorAuth = () => {
  setAuth(bearer, unauthorized);
  useEffect(() => {
    if (!state.ready) {
      userManager.getUser()
        .then((u) => (u && !u.expired ? set({ user: u, ready: true }) : trySilent() || set({ user: null, ready: true })))
        .catch(() => set({ ready: true }));
    }
  }, []);
};

/** Последний логин (не секрет) — подставляем в форму входа, чтобы оставалось ввести только код. */
const LAST_LOGIN = "documents.login";
const remember = (u: User) => {
  const login = u.profile.preferred_username;
  if (login) localStorage.setItem(LAST_LOGIN, login);
};
const signedIn = (u: User) => {
  sessionStorage.removeItem(SSO_TRIED); // вошли — при следующей перезагрузке снова можно тихо
  remember(u);
  set({ user: u, ready: true });
};

/**
 * FE-UI-02: вход внутри страницы — форма Keycloak во фрейме того же origin (/auth), без ухода с сайта.
 * Запрос и PKCE-проверка — те же, что у редиректа (state в sessionStorage); callback во фрейме
 * отдаёт адрес родителю (app/main.tsx), родитель меняет код на токены.
 */
export const embeddedSignInUrl = async (next: string) => {
  const req = await new OidcClient(userManager.settings).createSigninRequest({
    request_type: "si:r",
    state: { next },
    login_hint: localStorage.getItem(LAST_LOGIN) ?? undefined,
  });
  return req.url;
};
export const SIGNIN_MESSAGE = "documents.signin";
export const completeEmbeddedSignIn = async (url: string) => signedIn(await userManager.signinRedirectCallback(url));
export const forgetLogin = () => localStorage.removeItem(LAST_LOGIN);

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
    signIn: (next = location.pathname + location.search, loginHint?: string) =>
      userManager.signinRedirect({ state: { next }, login_hint: loginHint }),
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
      return sessionStorage.getItem(NEXT) ?? routes.operator;
    }
    throw e;
  }
  signedIn(u);
  const next = (u.state as { next?: string } | undefined)?.next;
  return next && next.startsWith(routes.operator) && next !== routes.operatorCallback ? next : routes.operator;
};
