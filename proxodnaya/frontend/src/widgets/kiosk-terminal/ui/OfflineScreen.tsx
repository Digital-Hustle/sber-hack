import { useState } from "react";
import { motion } from "motion/react";
import { ShieldCheck, WifiOff } from "lucide-react";
import { REASONS, type DecisionResult, type OfflinePolicy } from "@/shared/api";
import { Button, Dialog, Field, Input, Select, toast } from "@/shared/ui";
import { randomId } from "@/shared/lib";
import { useOfflinePass } from "@/features/offline-pass";
import { useTerminalCode } from "../model/useTerminalCode";
import { fadeUp, popIn, stagger, duration, ease } from "@/shared/config/motion";

const pulse = { duration: duration.loop, repeat: Infinity, ease: ease.inOut } as const;

/**
 * Нет связи с сервером (ADR-038, FR-70). Автоматического пропуска нет: решение принимает сервер (Д2),
 * а без него — только охранник, с причиной, отметкой MANUAL и отправкой в журнал после восстановления связи.
 */
export const OfflineScreen = ({ policy, checkpointId, onResult }: { policy: OfflinePolicy; checkpointId: string; onResult: (r: DecisionResult) => void }) => {
  const snap = useOfflinePass((s) => s.snapshot);
  const workers = snap?.workers ?? [];
  const [open, setOpen] = useState(false);
  const [who, setWho] = useState(workers[0]?.id ?? "");
  const [note, setNote] = useState("");
  const [pin, setPin] = useState("");
  const code = useTerminalCode("guard", { paired: true, offline: true });
  const [pinErr, setPinErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setBusy(true);
    const ok = await code.check(pin);
    if (!ok) { setBusy(false); setPin(""); setPinErr(code.left > 1 ? `Неверный код. Осталось попыток: ${code.left - 1}` : "Неверный код. Ввод заблокирован на минуту"); return; }
    // Без связи сервер недоступен: решение встаёт в подписанную очередь терминала и уйдёт в журнал на подтверждение.
    const ts = Date.now();
    const ev = await useOfflinePass.getState().push({ id: randomId("off"), ts, workerId: who, checkpointId, direction: "IN", decision: "MANUAL", code: "MANUAL_GUARD", note: `Без связи: ${note.trim()}` });
    const w = workers.find((x) => x.id === who);
    const r: DecisionResult = { attemptId: ev.id, decision: "MANUAL", code: "MANUAL_GUARD", message: REASONS.MANUAL_GUARD.message, hint: REASONS.MANUAL_GUARD.hint, direction: "IN", ts,
      worker: w ? { id: w.id, fullName: w.fullName, position: w.position, contractor: w.contractor } : undefined };
    setBusy(false); setOpen(false); setNote(""); setPin(""); setPinErr(null);
    toast.info("Ручной пропуск сохранён. Когда появится связь, он уйдёт в журнал на подтверждение");
    onResult(r);
  };
  return (
    <div className="relative isolate flex min-h-0 flex-1 flex-col">
      <motion.div variants={stagger(0.06)} initial="hidden" animate="show" className="relative flex flex-1 flex-col items-center justify-center gap-6 p-5 text-center text-white sm:p-8">
        <motion.span variants={popIn} className="relative flex size-20 items-center justify-center rounded-full bg-warning/20 text-warning">
          <motion.span aria-hidden className="absolute inset-0 rounded-full bg-warning/20" animate={{ scale: [1, 1.35], opacity: [0.6, 0] }} transition={pulse} />
          <WifiOff className="size-9" />
        </motion.span>
        <motion.div variants={fadeUp} className="flex max-w-xl flex-col gap-2">
          <h1 className="text-balance font-display text-3xl font-semibold tracking-display sm:text-4xl">Нет связи с сервером</h1>
          <p className="text-balance text-base text-white/70 sm:text-lg">{policy === "GUARD" ? "Автоматический проход временно недоступен. Обратитесь к охраннику — он пропустит вручную" : "Проход закрыт до восстановления связи. Обратитесь к охране"}</p>
        </motion.div>
        {policy === "GUARD" && <motion.div variants={fadeUp}><Button size="lg" variant="secondary" onClick={() => setOpen(true)}><ShieldCheck />Пропуск охранником</Button></motion.div>}
      </motion.div>
      <Dialog open={open} onClose={() => setOpen(false)} title="Пропуск охранником" description="Запись помечается как ручная, хранится на терминале и уходит в журнал, когда связь вернётся"
        footer={<Button disabled={!who || !note.trim() || !code.available || pin.length !== code.digits || busy || !!code.lockedSec} onClick={submit}><ShieldCheck />Пропустить</Button>}>
        <div className="flex flex-col gap-5">
          <Field label="Сотрудник" hint="Сверьте лицо с фото из снимка допусков"><Select value={who} onChange={setWho} options={workers.filter((w) => w.status === "active").map((w) => ({ value: w.id, label: w.fullName, hint: w.position }))} /></Field>
          <Field label="Причина"><Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Например: нет связи, личность подтверждена по паспорту" /></Field>
          <Field label="Код охранника" hint={!code.available ? "Код охранника не задан — задайте его в кабинете → Терминалы" : `${code.digits} цифр · проверяется на терминале, работает без связи`}
            error={code.lockedSec ? `Слишком много попыток. Повторите через ${code.lockedSec} с` : pinErr}>
            <Input type="password" inputMode="numeric" autoComplete="off" maxLength={code.digits} disabled={!code.available || !!code.lockedSec} value={pin} onChange={(e) => { setPin(e.target.value.replace(/\D/g, "")); setPinErr(null); }} placeholder={"•".repeat(code.digits)} />
          </Field>
        </div>
      </Dialog>
    </div>
  );
};
