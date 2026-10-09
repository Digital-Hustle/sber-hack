// Формат QR пропуска (docs/BACKEND-CONTRACT.md): v1.<workerId>.<deviceId>.<window>.<signature>
// Подписывается строка v1|workerId|deviceId|window ключом телефона. Открытый ключ сервер знает с активации,
// терминал без связи берёт его из снимка допусков.
import { sign, type StoredKey } from "./deviceKey";

export const QR_WINDOW_SEC = 30;
export const currentWindow = (now = Date.now()) => Math.floor(now / 1000 / QR_WINDOW_SEC);
export const windowEndsAt = (w: number) => (w + 1) * QR_WINDOW_SEC * 1000;
export const signedMessage = (workerId: string, deviceId: string, window: number) => `v1|${workerId}|${deviceId}|${window}`;

export type PassQr = { workerId: string; deviceId: string; window: number; signature: string };

export const buildPassQr = async (key: StoredKey, window = currentWindow()) => {
  const signature = await sign(key.privateKey, signedMessage(key.workerId, key.deviceId, window));
  return ["v1", key.workerId, key.deviceId, window, signature].join(".");
};

export const parsePassQr = (raw: string): PassQr | null => {
  const parts = raw.trim().split(".");
  if (parts.length !== 5 || parts[0] !== "v1") return null;
  const window = Number(parts[3]);
  if (!Number.isInteger(window) || !parts[4]) return null;
  return { workerId: parts[1], deviceId: parts[2], window, signature: parts[4] };
};
