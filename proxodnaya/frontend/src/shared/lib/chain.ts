// ADR-043: журнал терминала без связи — цепочка хэшей + подпись пакета ключом терминала.
// Канонический JSON (ключи по алфавиту) нужен, чтобы терминал и сервер хэшировали одинаковые байты.
import { toB64u } from "./b64";

export const GENESIS = "genesis";

export const canonical = (v: unknown): string => {
  if (v === null || typeof v !== "object") return JSON.stringify(v ?? null);
  if (Array.isArray(v)) return `[${v.map(canonical).join(",")}]`;
  const o = v as Record<string, unknown>;
  return `{${Object.keys(o).filter((k) => o[k] !== undefined).sort().map((k) => `${JSON.stringify(k)}:${canonical(o[k])}`).join(",")}}`;
};

export const sha256 = async (s: string) => toB64u(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));

/** Хэш звена: предыдущий хэш + содержимое записи без собственного хэша. Изменить, вставить или удалить запись незаметно нельзя. */
export const linkHash = <T extends { hash?: string }>(e: T) => {
  const { hash: _drop, ...body } = e;
  return sha256(canonical(body));
};
