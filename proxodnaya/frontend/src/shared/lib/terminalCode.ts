// ADR-046: коды на терминале — сервисный (инженер) и охранника (ручной пропуск без связи).
// Сервер хранит и раздаёт терминалам только солёный хэш PBKDF2, сам код нигде не лежит.
// Терминал проверяет код у себя, поэтому код охранника работает и без связи.
import { toB64u, fromB64u } from "./b64";

export type TerminalCodeKind = "service" | "guard";
export type TerminalCodeHash = { salt: string; hash: string; iter: number; digits: number; updatedAt: number; by: string };

/** Заводские коды: действуют, пока администратор не задал свои. Сервис — только до первой смены. */
export const FACTORY_CODES: Record<TerminalCodeKind, string> = { service: "2580", guard: "0000" };
export const CODE_ITER = 120_000;
export const CODE_MIN = 4;
export const CODE_MAX = 6;

const derive = async (code: string, salt: Uint8Array, iter: number) => {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(code), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: salt as BufferSource, iterations: iter }, key, 256);
  return toB64u(bits);
};

export const hashTerminalCode = async (code: string, by: string): Promise<TerminalCodeHash> => {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  return { salt: toB64u(salt), hash: await derive(code, salt, CODE_ITER), iter: CODE_ITER, digits: code.length, updatedAt: Date.now(), by };
};

/** Проверка без раннего выхода по символам — время сравнения не подсказывает, сколько цифр совпало. */
export const checkTerminalCode = async (code: string, kind: TerminalCodeKind, rec?: TerminalCodeHash) => {
  if (!rec) return kind === "service" && code === FACTORY_CODES.service;
  if (code.length !== rec.digits) return false;
  const h = await derive(code, fromB64u(rec.salt), rec.iter);
  let diff = h.length ^ rec.hash.length;
  for (let i = 0; i < Math.max(h.length, rec.hash.length); i++) diff |= (h.charCodeAt(i) || 0) ^ (rec.hash.charCodeAt(i) || 0);
  return diff === 0;
};

/** Слабые коды не принимаем: их подбирают первыми. Возвращает причину или null. */
export const weakTerminalCode = (code: string): string | null => {
  if (!/^\d+$/.test(code)) return "Только цифры";
  if (code.length < CODE_MIN || code.length > CODE_MAX) return `От ${CODE_MIN} до ${CODE_MAX} цифр`;
  if (/^(\d)\1+$/.test(code)) return "Все цифры одинаковые — такой код подберут первым";
  const d = [...code].map(Number);
  const step = d[1] - d[0];
  if (Math.abs(step) === 1 && d.every((v, i) => !i || v - d[i - 1] === step)) return "Цифры подряд — такой код подберут первым";
  if (code === FACTORY_CODES.service || code === FACTORY_CODES.guard) return "Это заводской код — придумайте свой";
  if (/^(19|20)\d\d$/.test(code)) return "Похоже на год — выберите другой код";
  return null;
};
