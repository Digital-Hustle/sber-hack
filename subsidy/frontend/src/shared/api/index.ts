import axios, { AxiosError } from "axios";

/** Ошибка API в формате RFC 7807 (docs/API.md): code, detail и hint — по-русски. */
export type Problem = { type?: string; title?: string; status: number; detail?: string; code?: string; hint?: string; traceId?: string; errors?: { field: string; message: string }[] };

/** Токен доступа хранится только в памяти (rules.md §4), выставляет слой входа. */
let accessToken: string | null = null;
export const setAccessToken = (t: string | null) => { accessToken = t; };

export const $api = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? "/", timeout: 30_000 });

$api.interceptors.request.use((cfg) => {
  if (accessToken) cfg.headers.Authorization = `Bearer ${accessToken}`;
  return cfg;
});

export const toProblem = (e: unknown): Problem => {
  if (e instanceof AxiosError) {
    if (!e.response) return { status: 0, code: "NETWORK", detail: "Нет связи с сервером", hint: "Проверьте подключение и повторите" };
    const d = e.response.data as Partial<Problem> | undefined;
    return { status: e.response.status, ...(d && typeof d === "object" ? d : {}) };
  }
  return { status: 0, code: "UNKNOWN", detail: "Что-то пошло не так" };
};
