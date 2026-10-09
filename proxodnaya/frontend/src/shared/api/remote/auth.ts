// Три способа авторизации (docs/API.md): персонал — JWT после входа по коду из письма, телефон — device-токен ES256,
// терминал — подпись запроса ключом киоска. Токены живут только в памяти (RULES.md ⛔ localStorage);
// после перезагрузки кабинет восстанавливает сессию по httpOnly-cookie refresh (people, /auth/staff/refresh).
import { http, setAuthProvider } from "./http";
import { loadKey, sign, sha256B64u, type StoredKey } from "../../lib/deviceKey";
import { randomId } from "../../lib/id";
import type { Role } from "../types";

export type StaffUser = { id: string; name: string; email: string; role: Role; status: "ACTIVE" | "INVITED" | "DISABLED"; lastSeen?: number };
type StaffSessionRs = { accessToken: string; refreshToken?: string; expiresAt: number; sessionExpiresAt: number; user: StaffUser };

// ——— Персонал ———
let staff: { accessToken: string; refreshToken?: string; expiresAt: number; sessionExpiresAt: number } | null = null;
let staffUser: StaffUser | null = null;
const staffListeners = new Set<() => void>();
const emitStaff = () => staffListeners.forEach((l) => l());
export const onStaffChange = (cb: () => void) => { staffListeners.add(cb); return () => { staffListeners.delete(cb); }; };
export const getStaffUser = () => staffUser;
export const getStaffSession = () => staff;

export const applyStaffSession = (r: StaffSessionRs) => {
  staff = { accessToken: r.accessToken, refreshToken: r.refreshToken, expiresAt: r.expiresAt, sessionExpiresAt: r.sessionExpiresAt };
  staffUser = r.user;
  emitStaff();
  return r.user;
};
export const clearStaffSession = () => { staff = null; staffUser = null; emitStaff(); };

let renewing: Promise<boolean> | null = null;
/** Обновить access-токен: refresh из памяти или httpOnly-cookie. Параллельные 401 ждут одного обновления. */
export const renewStaff = () => {
  renewing ??= http<StaffSessionRs>("people", "/auth/staff/refresh", { auth: "none", body: staff?.refreshToken ? { refreshToken: staff.refreshToken } : {} })
    .then((r) => { applyStaffSession(r); return true; })
    .catch(() => false)
    .finally(() => { renewing = null; });
  return renewing;
};

const REFRESH_EARLY_MS = 30_000;
setAuthProvider("staff", {
  headers: async () => {
    if (staff && staff.expiresAt - Date.now() < REFRESH_EARLY_MS) await renewStaff();
    return staff ? { Authorization: `Bearer ${staff.accessToken}` } : null;
  },
  renew: renewStaff,
  expired: clearStaffSession,
});

// ——— Телефон сотрудника ———
export const PHONE_SLOT = "phone";
let device: { token: string; expiresAt: number } | null = null;
let deviceRenew: Promise<boolean> | null = null;
/** challenge → подпись `auth|deviceId|challenge` ключом телефона → токен на 10 минут. */
export const renewDevice = () => {
  deviceRenew ??= (async () => {
    const key = await loadKey(PHONE_SLOT);
    if (!key) return false;
    const { challenge } = await http<{ challenge: string }>("people", "/auth/device/challenge", { auth: "none", body: { deviceId: key.deviceId } });
    const signature = await sign(key.privateKey, `auth|${key.deviceId}|${challenge}`);
    const r = await http<{ accessToken: string; expiresAt: number }>("people", "/auth/device/token", { auth: "none", body: { deviceId: key.deviceId, challenge, signature } });
    device = { token: r.accessToken, expiresAt: r.expiresAt };
    return true;
  })().catch(() => false).finally(() => { deviceRenew = null; });
  return deviceRenew;
};
export const clearDeviceToken = () => { device = null; };
setAuthProvider("device", {
  headers: async () => {
    if (!device || device.expiresAt - Date.now() < REFRESH_EARLY_MS) await renewDevice();
    return device ? { Authorization: `Bearer ${device.token}` } : null;
  },
  renew: async () => { device = null; return renewDevice(); },
});

// ——— Терминал ———
export const KIOSK_SLOT = "kiosk";
let kioskKey: StoredKey | null = null;
export const setKioskKey = (k: StoredKey | null) => { kioskKey = k; };
/**
 * Подпись запроса киоска (ACC-12): `kiosk|<kioskId>|<METHOD>|<path?query>|<ts>|<nonce>|<sha256(body)>`,
 * ECDSA P-256, неизвлекаемый ключ в IndexedDB. Сервер проверяет ключ, время (±60 с) и одноразовость nonce.
 */
export const kioskHeaders = async (method: string, path: string, body = "") => {
  const key = kioskKey ?? (await loadKey(KIOSK_SLOT)) ?? null;
  if (!key) return null;
  kioskKey = key;
  const ts = String(Date.now());
  const nonce = randomId("", 16);
  const msg = `kiosk|${key.deviceId}|${method}|${path}|${ts}|${nonce}|${await sha256B64u(body)}`;
  return { "X-Kiosk-Id": key.deviceId, "X-Ts": ts, "X-Nonce": nonce, "X-Signature": await sign(key.privateKey, msg) };
};
setAuthProvider("kiosk", { headers: (method, path, body) => kioskHeaders(method, path, body) });
