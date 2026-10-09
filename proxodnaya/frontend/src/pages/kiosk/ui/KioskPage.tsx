import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Maximize } from "lucide-react";
import { api, useDb, terminalModeOf, offlinePolicyOf, kioskIdentity, kioskStatus, KIOSK_SLOT, type DecisionResult } from "@/shared/api";
import { Button, Logo, toast } from "@/shared/ui";
import { useOfflinePass, localLeftMs, verifyLocal, syncQueue } from "@/features/offline-pass";
import { useNow, useOnline } from "@/shared/hooks";
import { hhmm, cn, loadKey } from "@/shared/lib";
import { tween } from "@/shared/config/motion";
import { KioskTerminal, PairingScreen, OfflineScreen, ServicePanel, DecisionScreen, MODE_LABEL } from "@/widgets/kiosk-terminal";

/** Киоск всегда в тёмной теме: экран у турникета не должен слепить. Тема сайта возвращается при уходе. */
const useForceDark = () => useEffect(() => {
  const html = document.documentElement;
  const had = html.classList.contains("dark");
  html.classList.add("dark");
  return () => { if (!had) html.classList.remove("dark"); };
}, []);

/** id терминала = id его ключа в IndexedDB. Первый запуск — регистрация на сервере; без связи — последний известный. */
const useKioskId = () => {
  const [id, setId] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    const tryId = () => kioskIdentity().then((x) => alive && setId(x)).catch(() => loadKey(KIOSK_SLOT).then((k) => { if (alive && k && k.deviceId !== "pending") setId(k.deviceId); }));
    void tryId();
    const t = setInterval(() => { if (alive) setId((cur) => { if (!cur) void tryId(); return cur; }); }, 15000);
    return () => { alive = false; clearInterval(t); };
  }, []);
  return id;
};

export const KioskPage = () => {
  useForceDark();
  const db = useDb();
  const now = useNow(10000);
  const netOnline = useOnline();
  const kioskId = useKioskId();
  const snapshot = useOfflinePass((x) => x.snapshot);
  const snap = snapshot && snapshot.kioskId === kioskId ? snapshot : null;
  const kiosk = db.kiosks?.find((k) => k.id === kioskId);
  // Без связи после перезагрузки сервер не спросить — проходная берётся из последнего снимка допусков.
  const checkpoint = db.checkpoints.find((c) => c.id === kiosk?.checkpointId) ?? (!kiosk ? snap?.checkpoint : undefined);
  const paired = kiosk ? !!kiosk.pairedAt && !!checkpoint : !!snap;
  const mode = terminalModeOf(db, kiosk);
  const [simOffline, setSimOffline] = useState(false);
  const online = netOnline && !simOffline;
  const [service, setService] = useState(false);
  const [offlineResult, setOfflineResult] = useState<DecisionResult | null>(null);
  const hold = useRef<ReturnType<typeof setTimeout>>(undefined);

  // Пульс: терминал спрашивает своё состояние (сопряжение, проходная, логика) — заодно админка видит, что он на связи.
  const pairedNow = !!kiosk?.pairedAt;
  useEffect(() => {
    if (!online || !kioskId) return;
    const tick = () => { kioskStatus().catch(() => undefined); };
    tick();
    const t = setInterval(tick, pairedNow ? 30000 : 5000);
    return () => clearInterval(t);
  }, [kioskId, online, pairedNow]);

  const startHold = () => { hold.current = setTimeout(() => setService(true), 1000); };
  const endHold = () => clearTimeout(hold.current);

  // ADR-042: пока есть связь, терминал отправляет накопленные проходы и обновляет снимок допусков раз в минуту.
  const settingsKey = JSON.stringify(db.settings);
  useEffect(() => {
    if (!online || !pairedNow || !kioskId) return;
    let alive = true;
    const tick = async () => {
      const { queue, setSnapshot } = useOfflinePass.getState();
      if (queue.length) {
        // ADR-043: очередь уходит подписанными пакетами; что сервер не принял, остаётся до следующей попытки.
        const r = await syncQueue(kioskId);
        if (!alive) return;
        if (r.synced) toast.info(r.conflicts ? `Проходы без связи ушли в журнал: ${r.synced}, с конфликтом: ${r.conflicts}` : `Проходы без связи ушли в журнал: ${r.synced}`);
        if (r.rejected) toast.error(`Сервер отклонил изменённые записи терминала: ${r.rejected}`);
        if (!r.ok) toast.error(`Проходы без связи не отправлены: ${r.error}`);
      }
      const s = await api.kioskSnapshot().catch(() => null);
      if (alive && s) setSnapshot(s);
    };
    tick();
    const t = setInterval(tick, 60000);
    return () => { alive = false; clearInterval(t); };
    // Снимок сам пишет snapshotAt в базу — зависим от содержимого настроек, а не от ссылки, иначе эффект перезапускается и снимок теряется.
  }, [online, pairedNow, kioskId, settingsKey]);

  const policy = offlinePolicyOf(db, kiosk);
  const leftMs = snap ? localLeftMs(snap, now) : 0;
  const local = policy === "LOCAL" && leftMs > 0;
  const fallback = policy === "LOCAL" ? (snap?.rules.afterExpiry ?? db.settings.offlineAfterExpiry ?? "GUARD") : policy;
  const view = !kioskId ? "boot" : !paired ? "pair" : online ? "live" : local ? "local" : "offline";

  return (
    <div className="flex h-dvh flex-col gap-3 bg-background/30 px-3 pb-3 pt-safe text-foreground sm:gap-4 sm:px-5 sm:pb-5">
      <header className="flex shrink-0 items-center gap-2 pt-3 sm:gap-3 sm:pt-4">
        <button type="button" aria-label="Сервисная панель (удерживайте)" onPointerDown={startHold} onPointerUp={endHold} onPointerLeave={endHold} onContextMenu={(e) => e.preventDefault()}
          className="shrink-0 select-none rounded-md outline-none focus-visible:ring-2 focus-visible:ring-ring" onKeyDown={(e) => { if (e.key === "Enter" && e.shiftKey) setService(true); }}>
          <Logo compact className="sm:hidden" /><Logo sub="терминал" className="hidden sm:inline-flex" />
        </button>
        {paired && (
          <div className="min-w-0 sm:ml-3">
            <div className="truncate text-sm font-medium">{checkpoint?.name}</div>
            <div className="hidden truncate text-xs text-muted-foreground sm:block">{MODE_LABEL[mode]}</div>
          </div>
        )}
        <div className="ml-auto flex shrink-0 items-center gap-2 sm:gap-3">
          <span className="hidden items-center gap-2 text-sm text-muted-foreground md:flex">
            <span className={cn("size-2 rounded-full", online ? "bg-success" : "bg-warning")} />{online ? "Связь есть" : local ? "Нет связи · автономно" : "Нет связи"}
          </span>
          <span className="font-display text-xl font-semibold tabular-nums tracking-display sm:text-2xl">{hhmm(now)}</span>
          <Button variant="quiet" size="icon-sm" aria-label="Во весь экран" className="hidden sm:inline-flex" onClick={() => document.documentElement.requestFullscreen?.().catch(() => undefined)}><Maximize /></Button>
        </div>
      </header>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div key={view + (kiosk?.pairCode ?? "")} className="relative flex min-h-0 flex-1 flex-col" initial={{ opacity: 0, scale: 0.98 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.98 }} transition={tween.base}>
          {view === "boot" && <div className="flex flex-1 items-center justify-center text-lg text-muted-foreground">{online ? "Регистрируем терминал…" : "Нет связи: терминал ещё не зарегистрирован"}</div>}
          {view === "pair" && <PairingScreen code={kiosk?.pairCode ?? "••••••"} />}
          {view === "live" && checkpoint && <KioskTerminal checkpointId={checkpoint.id} mode={mode} />}
          {view === "local" && checkpoint && <KioskTerminal checkpointId={checkpoint.id} mode="QR_ONLY" offline={{ verify: verifyLocal, leftMs }} />}
          {view === "offline" && checkpoint && <OfflineScreen policy={fallback} checkpointId={checkpoint.id} onResult={setOfflineResult} />}
          <AnimatePresence>{view === "offline" && offlineResult && <DecisionScreen key={offlineResult.attemptId} result={offlineResult} onDone={() => setOfflineResult(null)} />}</AnimatePresence>
        </motion.div>
      </AnimatePresence>
      <ServicePanel open={service} onClose={() => setService(false)} kioskId={kioskId ?? "—"} kiosk={kiosk} simOffline={simOffline} setSimOffline={setSimOffline} />
    </div>
  );
};
