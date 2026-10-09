import { create } from "zustand";
import type { OfflineEvent, OfflineSnapshot } from "@/shared/api";
import { GENESIS, linkHash } from "@/shared/lib";

// Хранилище терминала (ADR-042): снимок допусков с сервера, очередь проходов без связи и погашенные коды.
// Живёт на самом устройстве и переживает перезагрузку — иначе один и тот же QR можно было бы предъявить после рестарта.
const SNAP = "proxodnaya.kiosk.snapshot";
const QUEUE = "proxodnaya.kiosk.queue";
const USED = "proxodnaya.kiosk.used";
const CHAIN = "proxodnaya.kiosk.chain";
const USED_MAX = 2000;

const read = <T,>(k: string, d: T): T => {
  try { const v = localStorage.getItem(k); return v ? (JSON.parse(v) as T) : d; } catch { return d; }
};
const write = (k: string, v: unknown) => {
  try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* квота хранилища: снимок без фото невелик */ }
};

type OfflinePassState = {
  snapshot: OfflineSnapshot | null;
  queue: OfflineEvent[];
  used: string[];
  /** ADR-043: голова журнала терминала. Не сбрасывается вместе с кэшем — иначе пропажа записей была бы незаметна. */
  head: { seq: number; hash: string };
  setSnapshot: (s: OfflineSnapshot) => void;
  /** Дописывает решение в журнал: номер, хэш предыдущего звена, свой хэш. */
  push: (e: OfflineEvent) => Promise<OfflineEvent>;
  dropSynced: (ids: string[]) => void;
  reset: () => void;
};

let lock: Promise<unknown> = Promise.resolve();

export const useOfflinePass = create<OfflinePassState>((set, get) => ({
  snapshot: read<OfflineSnapshot | null>(SNAP, null),
  queue: read<OfflineEvent[]>(QUEUE, []),
  used: read<string[]>(USED, []),
  head: read(CHAIN, { seq: 0, hash: GENESIS }),
  setSnapshot: (snapshot) => { write(SNAP, snapshot); set({ snapshot }); },
  push: (e) => {
    // Записи сцепляются строго по очереди, даже если два скана пришли почти одновременно.
    const run = lock.then(async () => {
      const { head } = get();
      const body: OfflineEvent = { ...e, seq: head.seq + 1, prev: head.hash };
      const linked = { ...body, hash: await linkHash(body) };
      const queue = [...get().queue, linked];
      const used = e.useKey ? [...get().used, e.useKey].slice(-USED_MAX) : get().used;
      const next = { seq: linked.seq!, hash: linked.hash };
      write(QUEUE, queue); write(USED, used); write(CHAIN, next);
      set({ queue, used, head: next });
      return linked;
    });
    lock = run.catch(() => undefined);
    return run;
  },
  dropSynced: (ids) => {
    const queue = get().queue.filter((e) => !ids.includes(e.id));
    write(QUEUE, queue);
    set({ queue });
  },
  // Сбрасывает только кэш проверки. Неотправленные проходы и голова журнала остаются: потерять их молча нельзя.
  reset: () => {
    [SNAP, USED].forEach((k) => localStorage.removeItem(k));
    set({ snapshot: null, used: [] });
  },
}));

/** Сколько ещё терминал может пропускать сам: срок считается от момента последнего снимка. */
export const localLeftMs = (s: OfflineSnapshot | null, now = Date.now()) => (s ? Math.max(0, s.at + s.rules.maxHours * 3600000 - now) : 0);
