// Ключ устройства: ECDSA P-256, приватная часть неизвлекаемая и живёт в IndexedDB (ADR-028).
import { toB64u, fromB64u } from "./b64";

const DB = "proxodnaya-keys";
const STORE = "keys";
const ALG = { name: "ECDSA", namedCurve: "P-256" } as const;
const SIGN = { name: "ECDSA", hash: "SHA-256" } as const;

const open = () => new Promise<IDBDatabase>((resolve, reject) => {
  const req = indexedDB.open(DB, 1);
  req.onupgradeneeded = () => req.result.createObjectStore(STORE);
  req.onsuccess = () => resolve(req.result);
  req.onerror = () => reject(req.error);
});

const tx = async <T>(mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>) => {
  const db = await open();
  return new Promise<T>((resolve, reject) => {
    const r = fn(db.transaction(STORE, mode).objectStore(STORE));
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
};

export type StoredKey = { deviceId: string; workerId: string; privateKey: CryptoKey; publicKey: string; createdAt: number };

export const loadKey = (slot: string) => tx<StoredKey | undefined>("readonly", (s) => s.get(slot));
export const deleteKey = (slot: string) => tx("readwrite", (s) => s.delete(slot));

/** Создаёт пару ключей; наружу уходит только публичный ключ (raw, base64url). */
export const createKey = async (slot: string, deviceId: string, workerId: string) => {
  const pair = await crypto.subtle.generateKey(ALG, false, ["sign", "verify"]);
  const publicKey = toB64u(await crypto.subtle.exportKey("raw", pair.publicKey));
  const stored: StoredKey = { deviceId, workerId, privateKey: pair.privateKey, publicKey, createdAt: Date.now() };
  await tx("readwrite", (s) => s.put(stored, slot));
  return stored;
};

export const sign = async (key: CryptoKey, message: string) =>
  toB64u(await crypto.subtle.sign(SIGN, key, new TextEncoder().encode(message)));

export const verify = async (publicKeyB64u: string, signatureB64u: string, message: string) => {
  try {
    const key = await crypto.subtle.importKey("raw", fromB64u(publicKeyB64u), ALG, false, ["verify"]);
    return await crypto.subtle.verify(SIGN, key, fromB64u(signatureB64u), new TextEncoder().encode(message));
  } catch {
    return false;
  }
};

/** Публичный ключ из raw (base64url) → JWK EC P-256 без `d` — так его ждёт сервер при активации и сопряжении. */
export const publicJwk = async (publicKeyB64u: string): Promise<JsonWebKey> => {
  const key = await crypto.subtle.importKey("raw", fromB64u(publicKeyB64u), ALG, true, ["verify"]);
  const { kty, crv, x, y } = await crypto.subtle.exportKey("jwk", key);
  return { kty, crv, x, y };
};

/** Записать ключ под слотом заново (например, сервер выдал workerId или kioskId после регистрации). */
export const saveKey = (slot: string, key: StoredKey) => tx("readwrite", (s) => s.put(key, slot));

export const sha256B64u = async (text: string) => toB64u(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
