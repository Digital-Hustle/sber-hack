import { api, KIOSK_SLOT, type OfflineBatch, type OfflineSyncResult } from "@/shared/api";
import { canonical, loadKey, randomId, sign, type StoredKey } from "@/shared/lib";
import { useOfflinePass } from "./store";

// ADR-043: отправка проходов без связи пакетами, подписанными ключом терминала.
/** Сколько записей в одном пакете: длинная очередь уходит частями, каждая подтверждается отдельно. */
export const BATCH_MAX = 50;

/** Ключ терминала создаётся при регистрации (kioskIdentity), закрытая часть неизвлекаемая и живёт в IndexedDB. */
const kioskKey = async () => {
  const k = await loadKey(KIOSK_SLOT);
  if (!k) throw new Error("Терминал не зарегистрирован");
  return k;
};

export const signBatch = async (key: StoredKey, batch: OfflineBatch) => sign(key.privateKey, canonical(batch));

let running: Promise<OfflineSyncResult> | null = null;

/**
 * Отправляет очередь пакетами до первой ошибки. Сервер отвечает списком принятых записей — только их терминал
 * убирает из очереди; остальное уйдёт при следующей попытке. Повторная отправка безопасна: записи идемпотентны по id.
 */
export const syncQueue = (kioskId: string): Promise<OfflineSyncResult> => {
  running ??= (async () => {
    const total: OfflineSyncResult = { ok: true, accepted: [], synced: 0, conflicts: 0, rejected: 0 };
    try {
      const key = await kioskKey();
      for (let guard = 0; guard < 100; guard++) {
        const events = useOfflinePass.getState().queue.slice(0, BATCH_MAX);
        if (!events.length) break;
        const batch: OfflineBatch = { v: 1, kioskId, nonce: randomId("b", 10), sentAt: Date.now(), events };
        const r = await api.syncOffline(batch, await signBatch(key, batch));
        useOfflinePass.getState().dropSynced(r.accepted);
        total.accepted.push(...r.accepted); total.synced += r.synced; total.conflicts += r.conflicts; total.rejected += r.rejected;
        if (!r.ok) return { ...total, ok: false, error: r.error };
        if (!r.accepted.length) break;
      }
      return total;
    } catch (e) {
      return { ...total, ok: false, error: e instanceof Error ? e.message : "Сервер недоступен" };
    } finally {
      running = null;
    }
  })();
  return running;
};
