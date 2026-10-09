import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { motion } from "motion/react";
import { Link2, MonitorSmartphone, Pencil, Unlink, ScanLine, KeyRound, ShieldCheck, Wrench } from "lucide-react";
import { api, useDb, KIOSK_ONLINE_MS, terminalModeOf, CODE_ROLES, type Kiosk, type TerminalMode, type OfflinePolicy, type TerminalCodeKind } from "@/shared/api";
import { useSession } from "@/entities/session";
import { Button, Card, CardHeader, CardTitle, Dialog, useDialogState, EmptyState, Field, Input, PageHeader, Select, Status, toast } from "@/shared/ui";
import { useNow } from "@/shared/hooks";
import { agoRu, weakTerminalCode, CODE_MIN, CODE_MAX } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { fadeUp, stagger } from "@/shared/config/motion";
import { MODE_LABEL } from "@/widgets/kiosk-terminal";

type ModeOpt = TerminalMode | "DEFAULT";

const useModeOptions = () => {
  const { settings } = useDb();
  const def = settings.terminalMode ?? "QR_FACE";
  return [
    { value: "DEFAULT" as ModeOpt, label: "Как в настройках", hint: MODE_LABEL[def] },
    { value: "QR_FACE" as ModeOpt, label: MODE_LABEL.QR_FACE, hint: "рекомендуется" },
    { value: "FACE_FIRST" as ModeOpt, label: MODE_LABEL.FACE_FIRST },
    { value: "QR_ONLY" as ModeOpt, label: MODE_LABEL.QR_ONLY, hint: "без сверки лица" },
  ];
};

type OffOpt = OfflinePolicy | "DEFAULT";
const OFF_LABEL: Record<OfflinePolicy, string> = { GUARD: "Пропуск охранником", CLOSED: "Проход закрыт", LOCAL: "Автономная проверка QR" };
const useOfflineOptions = () => {
  const { settings } = useDb();
  const def = settings.offlinePolicy ?? "GUARD";
  return [
    { value: "DEFAULT" as OffOpt, label: "Как в настройках", hint: OFF_LABEL[def] },
    { value: "GUARD" as OffOpt, label: OFF_LABEL.GUARD, hint: "рекомендуется" },
    { value: "CLOSED" as OffOpt, label: OFF_LABEL.CLOSED },
    { value: "LOCAL" as OffOpt, label: OFF_LABEL.LOCAL, hint: "по снимку допусков" },
  ];
};
const usePerKiosk = () => useDb().settings.terminalScope === "PER_KIOSK";

/** Общая логика: на терминале её не выбрать — показываем, что действует, и где поменять. */
const GlobalNote = () => {
  const { settings } = useDb();
  return (
    <div className="rounded-lg bg-surface p-4 text-sm">
      <div className="font-medium">{MODE_LABEL[settings.terminalMode ?? "QR_FACE"]} · без связи: {OFF_LABEL[settings.offlinePolicy ?? "GUARD"].toLowerCase()}</div>
      <div className="text-pretty text-muted-foreground">Логика одна для всех терминалов. Чтобы задавать её каждому киоску отдельно, включите это в <Link to={routes.adminSettings} className="font-medium text-foreground underline-offset-4 hover:underline">настройках</Link></div>
    </div>
  );
};

const EditDialog = ({ kiosk, onClose: onClosed }: { kiosk: Kiosk; onClose: () => void }) => {
  const { open, close: onClose } = useDialogState();
  const db = useDb();
  const modes = useModeOptions();
  const [name, setName] = useState(kiosk.name ?? "");
  const [cp, setCp] = useState(kiosk.checkpointId ?? db.checkpoints[0]?.id ?? "");
  const [mode, setMode] = useState<ModeOpt>(kiosk.mode ?? "DEFAULT");
  const offs = useOfflineOptions();
  const per = usePerKiosk();
  const [off, setOff] = useState<OffOpt>(kiosk.offlinePolicy ?? "DEFAULT");
  const save = async () => { await api.updateKiosk(kiosk.id, { name: name.trim() || "Терминал", checkpointId: cp, ...(per ? { mode: mode === "DEFAULT" ? undefined : mode, offlinePolicy: off === "DEFAULT" ? undefined : off } : {}) }); toast.success("Терминал обновлён"); onClose(); };
  return (
    <Dialog open={open} onClose={onClose} onClosed={onClosed} title="Настройка терминала" description="Изменения применяются на киоске сразу"
      footer={<><Button variant="quiet" onClick={onClose}>Отмена</Button><Button onClick={save}>Сохранить</Button></>}>
      <div className="flex flex-col gap-5">
        <Field label="Название"><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Проходная"><Select value={cp} onChange={setCp} options={db.checkpoints.map((c) => ({ value: c.id, label: c.name }))} /></Field>
        {per ? <>
          <Field label="Логика работы"><Select value={mode} onChange={setMode} options={modes} /></Field>
          <Field label="Без связи с сервером"><Select value={off} onChange={setOff} options={offs} /></Field>
        </> : <GlobalNote />}
      </div>
    </Dialog>
  );
};

const CODES: { kind: TerminalCodeKind; title: string; text: string; icon: typeof KeyRound }[] = [
  { kind: "service", title: "Сервисный код", text: "Открывает сервисную панель киоска: состояние, отвязка, демо-пульт. Нужен инженеру терминалов.", icon: Wrench },
  { kind: "guard", title: "Код охранника", text: "Подтверждает ручной пропуск, когда у терминала нет связи. Проверяется на самом киоске.", icon: ShieldCheck },
];

const CodeDialog = ({ kind, onClose: onClosed }: { kind: TerminalCodeKind; onClose: () => void }) => {
  const { open, close: onClose } = useDialogState();
  const userId = useSession((x) => x.userId);
  const [a, setA] = useState("");
  const [b, setB] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const weak = a.length >= CODE_MIN ? weakTerminalCode(a) : null;
  const mismatch = b.length >= a.length && a.length >= CODE_MIN && a !== b;
  const ok = !weak && a.length >= CODE_MIN && a === b;
  const save = async () => {
    setBusy(true); setErr(null);
    try { await api.setTerminalCode(kind, a, userId); toast.success("Код сменён. Терминалы получат его при следующей связи с сервером"); onClose(); }
    catch (x) { setErr(x instanceof Error ? x.message : "Не удалось сменить код"); } finally { setBusy(false); }
  };
  const digits = (v: string) => v.replace(/\D/g, "").slice(0, CODE_MAX);
  return (
    <Dialog open={open} onClose={onClose} onClosed={onClosed} title={kind === "service" ? "Новый сервисный код" : "Новый код охранника"}
      description="Код один для всех терминалов. Сообщите его только тем, кому он нужен: в журнале доступа останется, кто и когда его сменил"
      footer={<><Button variant="quiet" onClick={onClose}>Отмена</Button><Button disabled={!ok || busy} onClick={save}><KeyRound />Сменить код</Button></>}>
      <form className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); if (ok) save(); }}>
        <Field label="Новый код" hint={`От ${CODE_MIN} до ${CODE_MAX} цифр`} error={weak ?? err}>
          <Input type="password" inputMode="numeric" autoComplete="new-password" autoFocus value={a} onChange={(e) => { setA(digits(e.target.value)); setErr(null); }} placeholder="••••" className="tracking-widest" />
        </Field>
        <Field label="Повторите код" error={mismatch ? "Коды не совпадают" : null}>
          <Input type="password" inputMode="numeric" autoComplete="new-password" value={b} onChange={(e) => setB(digits(e.target.value))} placeholder="••••" className="tracking-widest" />
        </Field>
        <button type="submit" hidden />
      </form>
    </Dialog>
  );
};

/** ADR-046: коды на терминалах. Заводские подсвечиваются — их надо сменить до запуска объекта. */
const CodesCard = () => {
  const db = useDb();
  const role = useSession((x) => x.role);
  const now = useNow(30000);
  const [edit, setEdit] = useState<TerminalCodeKind | null>(null);
  const nameOf = (id: string) => db.admins?.find((u) => u.id === id)?.name ?? "администратор";
  return (
    <Card>
      <CardHeader><CardTitle>Коды на терминалах</CardTitle></CardHeader>
      <ul className="divide-y divide-border">
        {CODES.map(({ kind, title, text, icon: Icon }) => {
          const rec = db.terminalCodes?.[kind];
          const allowed = CODE_ROLES[kind].includes(role);
          const action = allowed ? <Button variant={rec ? "secondary" : "primary"} size="sm" onClick={() => setEdit(kind)}><KeyRound />{rec ? "Сменить" : "Задать код"}</Button>
            : <span className="text-xs text-muted-foreground">Меняет администратор</span>;
          return (
            <li key={kind} className="flex items-start gap-3 px-4 py-4 sm:items-center sm:px-6">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-surface text-muted-foreground"><Icon className="size-5" /></span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2 font-medium">{title}{rec ? <Status tone="success">задан</Status> : <Status tone="warning">заводской</Status>}</div>
                <p className="text-pretty text-sm text-muted-foreground">{text}</p>
                <p className="mt-1 text-xs text-subtle-foreground">{rec ? `Сменил ${nameOf(rec.by)} ${agoRu(rec.updatedAt, now)} · ${rec.digits} цифр` : "Действует заводской код — смените его до запуска объекта"}</p>
                <div className="mt-3 sm:hidden">{action}</div>
              </div>
              <div className="hidden shrink-0 sm:block">{action}</div>
            </li>
          );
        })}
      </ul>
      <p className="border-t border-border px-4 py-3 text-pretty text-xs text-muted-foreground sm:px-6">Сервер хранит только хэш кода. Терминал получает его вместе со снимком допусков, поэтому код охранника работает и без связи. После 5 неверных попыток ввод на киоске блокируется на минуту.</p>
      {edit && <CodeDialog kind={edit} onClose={() => setEdit(null)} />}
    </Card>
  );
};

/** Терминалы: привязка киоска по коду с его экрана, выбор проходной и логики работы (ADR-038). */
export const TerminalsPage = () => {
  const db = useDb();
  const now = useNow(5000);
  const modes = useModeOptions();
  const [sp, setSp] = useSearchParams();
  const [code, setCode] = useState((sp.get("code") ?? "").toUpperCase());
  const [name, setName] = useState("");
  const [cp, setCp] = useState(db.checkpoints[0]?.id ?? "");
  const [mode, setMode] = useState<ModeOpt>("DEFAULT");
  const offs = useOfflineOptions();
  const per = usePerKiosk();
  const [off, setOff] = useState<OffOpt>("DEFAULT");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [edit, setEdit] = useState<Kiosk | null>(null);
  const kiosks = db.kiosks ?? [];
  const paired = kiosks.filter((k) => k.pairedAt).sort((a, b) => (b.pairedAt ?? 0) - (a.pairedAt ?? 0));
  const waiting = kiosks.filter((k) => !k.pairedAt && now - k.lastSeen < KIOSK_ONLINE_MS * 2);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setErr(null); setBusy(true);
    try {
      await api.pairKiosk(code, { name, checkpointId: cp, ...(per ? { mode: mode === "DEFAULT" ? undefined : mode, offlinePolicy: off === "DEFAULT" ? undefined : off } : {}) });
      toast.success("Терминал подключён — можно проходить");
      setCode(""); setName("");
      if (sp.has("code")) { sp.delete("code"); setSp(sp, { replace: true }); }
    } catch (x) { setErr(x instanceof Error ? x.message : "Не удалось привязать"); } finally { setBusy(false); }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Терминалы" sub="Киоски у турникетов: привязка к проходной и логика работы"
        actions={<Link to={routes.kiosk} target="_blank" tabIndex={-1}><Button variant="secondary"><ScanLine />Открыть новый киоск</Button></Link>} />
      <motion.div variants={stagger()} initial="hidden" animate="show" className="flex flex-col gap-3 sm:gap-4">
        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Подключить терминал</CardTitle></CardHeader>
          <form onSubmit={submit} className="flex flex-col gap-5 p-4 sm:p-6">
            <p className="text-pretty text-sm text-muted-foreground">Новый киоск показывает 6-значный код и QR. Отсканируйте QR телефоном или введите код. Пока киоск не привязан, он никого не пропускает, а проходную и логику нельзя поменять с самого киоска.</p>
            {waiting.length > 0 && (
              <div className="flex flex-wrap items-center gap-2 text-sm"><span className="text-muted-foreground">Ждут привязки:</span>
                {waiting.map((k) => <Button key={k.id} type="button" size="sm" variant={code === k.pairCode ? "primary" : "outline"} className="font-mono" onClick={() => setCode(k.pairCode)}>{k.pairCode}</Button>)}
              </div>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Код с экрана киоска" error={err}><Input value={code} maxLength={6} onChange={(e) => { setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "")); setErr(null); }} placeholder="Например, K7Q2MX" className="font-mono tracking-widest" autoComplete="off" /></Field>
              <Field label="Название"><Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Например, Турникет 1" /></Field>
              <Field label="Проходная" hint={db.checkpoints.length ? undefined : "Проходных нет — создайте их в разделе «Объекты»"}><Select value={cp} onChange={setCp} options={db.checkpoints.map((c) => ({ value: c.id, label: c.name }))} /></Field>
              {per ? <>
                <Field label="Логика работы" hint="По умолчанию — как в настройках"><Select value={mode} onChange={setMode} options={modes} /></Field>
                <Field label="Без связи с сервером" hint="По умолчанию — как в настройках"><Select value={off} onChange={setOff} options={offs} /></Field>
              </> : <div className="sm:col-span-2"><GlobalNote /></div>}
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <Button type="submit" disabled={code.length !== 6 || !cp || busy}><Link2 />Привязать</Button>
            </div>
          </form>
        </Card></motion.div>

        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Подключённые терминалы</CardTitle></CardHeader>
          {paired.length === 0 ? <EmptyState icon={<MonitorSmartphone />} title="Пока ни одного" text="Откройте киоск на планшете у турникета и введите код с его экрана" /> : (
            <ul className="divide-y divide-border">
              {paired.map((k) => {
                const online = now - k.lastSeen < KIOSK_ONLINE_MS;
                return (
                  <li key={k.id} className="flex flex-wrap items-center gap-3 px-4 py-3.5 sm:px-6">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-surface text-muted-foreground"><MonitorSmartphone className="size-5" /></span>
                    <div className="min-w-0 flex-1">
                      <div className="truncate font-medium">{k.name}</div>
                      <div className="truncate text-sm text-muted-foreground">{db.checkpoints.find((c) => c.id === k.checkpointId)?.name ?? "—"} · {MODE_LABEL[terminalModeOf(db, k)]}{per && (k.mode || k.offlinePolicy) ? " · своя логика" : " · общая логика"}</div>
                    </div>
                    {online ? <Status tone="success">на связи</Status> : <Status tone="neutral">{`был ${agoRu(k.lastSeen)}`}</Status>}
                    <div className="flex gap-1">
                      <Button size="icon-sm" variant="quiet" aria-label="Настроить" onClick={() => setEdit(k)}><Pencil /></Button>
                      <Button size="icon-sm" variant="quiet" aria-label="Отвязать" onClick={() => api.unpairKiosk(k.id).then(() => toast.info("Терминал отвязан и больше не пропускает"))}><Unlink /></Button>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Card></motion.div>
        <motion.div variants={fadeUp}><CodesCard /></motion.div>
      </motion.div>
      {edit && <EditDialog kiosk={edit} onClose={() => setEdit(null)} />}
    </div>
  );
};
