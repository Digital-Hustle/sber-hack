// HTTP к сервисам через gateway: /<svc>/api/v1/…. Ошибки — problem+json → ApiError (detail показывается как есть).
import { ApiError, networkError, toApiError } from "./problem";

export type Service = "people" | "access" | "shift" | "assistant";
/** staff — токен кабинета, device — токен телефона, kiosk — подпись терминала, none — без авторизации. */
export type AuthMode = "staff" | "device" | "kiosk" | "none";

export type RequestOpts = {
  method?: string; body?: unknown; headers?: Record<string, string>; signal?: AbortSignal; auth?: AuthMode; query?: Record<string, unknown>;
};

type Provider = {
  /** Заголовки авторизации; null — входа нет. */
  headers: (method: string, path: string, body: string | undefined) => Promise<Record<string, string> | null>;
  /** 401: обновить токен и повторить один раз. true — повторять. */
  renew?: () => Promise<boolean>;
  /** Окончательный 401 — вход закрыт. */
  expired?: () => void;
};
const providers: Partial<Record<AuthMode, Provider>> = {};
export const setAuthProvider = (mode: AuthMode, p: Provider) => { providers[mode] = p; };

export const apiPath = (svc: Service, path: string) => `/${svc}/api/v1${path}`;

const qs = (q?: Record<string, unknown>) => {
  if (!q) return "";
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(q)) if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : "";
};

const send = async (svc: Service, path: string, o: RequestOpts, retry = true): Promise<Response> => {
  const method = o.method ?? (o.body === undefined ? "GET" : "POST");
  const url = apiPath(svc, path) + qs(o.query);
  const headers: Record<string, string> = { Accept: "application/json", ...o.headers };
  let body: BodyInit | undefined;
  let text: string | undefined;
  if (o.body instanceof FormData) body = o.body;
  else if (o.body !== undefined) { text = JSON.stringify(o.body); body = text; headers["Content-Type"] = "application/json"; }
  const mode = o.auth ?? "staff";
  const provider = providers[mode];
  if (mode !== "none" && provider) Object.assign(headers, (await provider.headers(method, url, text)) ?? {});
  let res: Response;
  try {
    res = await fetch(url, { method, headers, body, signal: o.signal, credentials: "same-origin" });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw networkError();
  }
  if (res.status === 401 && mode !== "none" && provider) {
    if (retry && provider.renew && (await provider.renew())) return send(svc, path, o, false);
    provider.expired?.();
  }
  if (!res.ok) throw await toApiError(res);
  return res;
};

/** JSON-запрос. 204 → undefined. */
export const http = async <T,>(svc: Service, path: string, o: RequestOpts = {}): Promise<T> => {
  const res = await send(svc, path, o);
  if (res.status === 204) return undefined as T;
  const t = await res.text();
  return (t ? JSON.parse(t) : undefined) as T;
};

/** Сырой ответ (CSV, XLSX, поток SSE). */
export const httpRaw = (svc: Service, path: string, o: RequestOpts = {}) => send(svc, path, o);

/** Скачать файл: имя — из Content-Disposition или запасное. */
export const download = async (svc: Service, path: string, fallback: string, query?: Record<string, unknown>) => {
  const res = await send(svc, path, { headers: { Accept: "*/*" }, query });
  const cd = res.headers.get("Content-Disposition") ?? "";
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd);
  const name = m ? decodeURIComponent(m[1]) : fallback;
  const href = URL.createObjectURL(await res.blob());
  const a = Object.assign(document.createElement("a"), { href, download: name });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
};

/** Обойти все страницы коллекции `{items, page: {next}}` (для справочников, не для журнала). */
export const fetchAll = async <T,>(svc: Service, path: string, query: Record<string, unknown> = {}, cap = 5000, auth: AuthMode = "staff"): Promise<T[]> => {
  const out: T[] = [];
  let cursor: string | null | undefined;
  do {
    const r = await http<{ items: T[]; page?: { next?: string | null } }>(svc, path, { query: { ...query, limit: 200, cursor }, auth });
    out.push(...r.items);
    cursor = r.page?.next;
  } while (cursor && out.length < cap);
  return out;
};

/** Время с сервера: ISO-строка или Unix ms → ms. */
export const ms = (v: string | number | null | undefined): number | undefined =>
  v === null || v === undefined ? undefined : typeof v === "number" ? v : Date.parse(v);

export { ApiError };
