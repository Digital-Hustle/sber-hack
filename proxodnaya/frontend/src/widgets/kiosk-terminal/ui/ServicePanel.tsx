import { useEffect, useState } from "react";
import { Link } from "react-router";
import { Unlink, ExternalLink, WifiOff, RotateCcw } from "lucide-react";
import { useDb, terminalModeOf, offlinePolicyOf, KIOSK_SLOT, type Kiosk } from "@/shared/api";
import { Button, Dialog, Field, Input, SwitchRow, Status, toast, InstallButton } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { agoRu, deleteKey, FACTORY_CODES } from "@/shared/lib";
import { useOfflinePass } from "@/features/offline-pass";
import { useTerminalCode } from "../model/useTerminalCode";

export const MODE_LABEL = { QR_FACE: "QR + лицо", FACE_FIRST: "Сначала лицо, QR — запасной", QR_ONLY: "Только QR" } as const;
export const OFFLINE_LABEL = { GUARD: "Пропуск охранником", CLOSED: "Проход закрыт", LOCAL: "Автономная проверка QR" } as const;

const Row = ({ k, v }: { k: string; v: React.ReactNode }) => (
  <div className="flex items-center justify-between gap-3 py-2.5 text-sm"><span className="text-muted-foreground">{k}</span><span className="min-w-0 truncate text-right font-medium">{v}</span></div>
);

/** Сервисная панель терминала для инженера: состояние, проверка без сети, сброс ключа. Защищена кодом. */
export const ServicePanel = ({ open, onClose, kioskId, kiosk, simOffline, setSimOffline }: {
  open: boolean; onClose: () => void; kioskId: string; kiosk?: Kiosk; simOffline: boolean; setSimOffline: (v: boolean) => void;
}) => {
  const db = useDb();
  const [pin, setPin] = useState("");
  const [ok, setOk] = useState(false);
  const close = () => { onClose(); setPin(""); setOk(false); setErr(null); };
  const cp = db.checkpoints.find((c) => c.id === kiosk?.checkpointId);
  const off = useOfflinePass();
  const code = useTerminalCode("service", { paired: !!kiosk?.pairedAt });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => { setOk(false); setPin(""); }, [db.terminalCodes?.service?.updatedAt, kiosk?.pairedAt]);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true);
    const okay = await code.check(pin);
    setBusy(false);
    if (okay) { setOk(true); setErr(null); return; }
    setPin(""); setErr(code.left > 1 ? `Неверный код. Осталось попыток: ${code.left - 1}` : "Неверный код. Ввод заблокирован на минуту");
  };
  return (
    <Dialog open={open} onClose={close} title="Сервисная панель" description={ok ? "Для инженера терминалов" : "Введите сервисный код"}>
      {!ok ? (
        <form className="flex flex-col gap-4" onSubmit={submit}>
          <Field label="Сервисный код" hint={!code.available ? "Сервисный код не задан — задайте его в кабинете → Терминалы" : code.factory ? `Действует заводской код ${FACTORY_CODES.service}. Смените его в админке → Терминалы` : `${code.digits} цифр · задаётся в админке → Терминалы`}
            error={code.lockedSec ? `Слишком много попыток. Повторите через ${code.lockedSec} с` : err}>
            <Input type="password" inputMode="numeric" autoComplete="off" maxLength={code.digits} autoFocus disabled={!code.available || !!code.lockedSec} value={pin} onChange={(e) => { setPin(e.target.value.replace(/\D/g, "")); setErr(null); }} placeholder={"•".repeat(code.digits)} />
          </Field>
          <Button type="submit" disabled={!code.available || pin.length !== code.digits || busy || !!code.lockedSec}>Открыть</Button>
        </form>
      ) : (
        <div className="flex flex-col gap-5">
          <div className="flex flex-col divide-y divide-border rounded-md border border-border px-3">
            <Row k="Идентификатор" v={<span className="font-mono text-xs">{kioskId}</span>} />
            <Row k="Статус" v={kiosk?.pairedAt ? <Status tone="success">привязан</Status> : <Status tone="warning">ждёт привязки · {kiosk?.pairCode}</Status>} />
            {kiosk?.pairedAt && <Row k="Название" v={kiosk.name} />}
            {kiosk?.pairedAt && <Row k="Проходная" v={cp?.name ?? "—"} />}
            <Row k="Логика работы" v={MODE_LABEL[terminalModeOf(db, kiosk)]} />
            <Row k="Без связи" v={OFFLINE_LABEL[offlinePolicyOf(db, kiosk)]} />
            <Row k="Снимок допусков" v={off.snapshot ? `${agoRu(off.snapshot.at)} · ${off.snapshot.workers.length} чел.` : "ещё не скачан"} />
            <Row k="Ждут отправки" v={off.queue.length ? `${off.queue.length} прох.` : "нет"} />
            <Row k="Коды" v={code.factory ? <Status tone="warning">заводские</Status> : "заданы в админке"} />
            <Row k="Журнал терминала" v={off.head.seq ? `${off.head.seq} зап. · подписан` : "пуст"} />
            {kiosk?.syncError && <Row k="Отправка" v={<Status tone="danger">{kiosk.syncError.message}</Status>} />}
            {kiosk?.pairedAt && <Row k="Привязан" v={agoRu(kiosk.pairedAt)} />}
          </div>
          <p className="text-xs text-muted-foreground">Проходная и логика работы меняются только в админке: на самом терминале их нельзя подменить.</p>
          <div className="rounded-md border border-border px-3"><SwitchRow icon={<WifiOff />} title="Имитировать обрыв связи" text="Проверка поведения без сети" checked={simOffline} onChange={setSimOffline} /></div>
          <InstallButton label="Установить терминал как приложение" className="w-full" />
          <Button variant="secondary" disabled={!off.snapshot} onClick={() => { off.reset(); toast.info("Снимок допусков сброшен. Неотправленные проходы сохранены"); }}><RotateCcw />Сбросить снимок допусков</Button>
          <div className="grid gap-2 sm:grid-cols-2">
            <Link to={routes.adminTerminals} target="_blank" tabIndex={-1} className="contents"><Button variant="secondary"><ExternalLink />В админке</Button></Link>
            <Button variant="danger-soft" onClick={() => { if (!confirm("Сбросить ключ терминала? Он получит новый код сопряжения, старую запись удалите в админке.")) return; void deleteKey(KIOSK_SLOT).then(() => location.reload()); }}><Unlink />Сбросить ключ</Button>
          </div>
        </div>
      )}
    </Dialog>
  );
};
