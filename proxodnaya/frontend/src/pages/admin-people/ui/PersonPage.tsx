import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { motion } from "motion/react";
import { AlertTriangle, ArrowLeft, Ban, Camera, Check, CheckCircle2, Pencil, QrCode, ScanFace, Smartphone, Trash2, UserX, X } from "lucide-react";
import { useSession, can } from "@/entities/session";
import { api, useDb, presenceNow, workedMs, buildIntervals, shiftFor, siteOfZone, type Worker } from "@/shared/api";
import { Avatar, Status, Button, Card, CardHeader, CardTitle, Dialog, useDialogState, EmptyState, Field, Input, Chip, toast } from "@/shared/ui";
import { FaceReferenceCapture, type FaceCheckState } from "@/features/capture-photo";
import { DatePicker } from "@/shared/ui";
import { AttemptRow, directionSource } from "@/entities/pass";
import { WorkerStatusBadge } from "@/entities/worker";
import { dateRu, durationRu, todayKey } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { fadeUp, stagger } from "@/shared/config/motion";
import { InviteCard } from "./InviteCard";

const FACE_TEXT: Record<string, string> = { HR: "снят в кабинете при человеке", PHONE: "селфи с телефона", KIOSK: "снят на терминале" };

/** ADR-046: эталон лица. Селфи с телефона включается только после того, как человек сверит его с документом. */
const FaceCard = ({ id }: { id: string }) => {
  const db = useDb();
  const by = useSession((x) => x.userId);
  const [busy, setBusy] = useState(false);
  const [shoot, setShoot] = useState(false);
  const w = db.workers.find((x) => x.id === id);
  const f = w?.face ?? { status: "ACTIVE" as const };
  if (!w) return null;
  const dup = f.status === "PENDING" && f.dupOf ? db.workers.find((x) => x.id === f.dupOf) : undefined;
  const act = (ok: boolean) => { setBusy(true); api.reviewFace(w.id, ok, by, ok ? undefined : "Лицо плохо видно или не совпадает с документом").then(() => { setBusy(false); toast[ok ? "success" : "info"](ok ? "Эталон подтверждён — терминалы начнут узнавать сотрудника" : "Снимок отклонён, сотрудник получит подсказку переснять"); }); };
  return (
    <Card>
      <CardHeader><CardTitle>Лицо для прохода</CardTitle>
        {f.status === "ACTIVE" ? <Status tone="success">есть</Status> : f.status === "PENDING" ? <Status tone="info">на проверке</Status> : f.status === "REJECTED" ? <Status tone="danger">отклонено</Status> : <Status tone="warning">нет</Status>}
      </CardHeader>
      <div className="flex flex-col gap-4 p-4 sm:p-6">
        {f.status === "PENDING" && f.pendingPhoto ? (
          <>
            <div className="flex items-center gap-4">
              <img src={f.pendingPhoto} alt="Селфи сотрудника" className="size-20 shrink-0 rounded-full object-cover ring-2 ring-border" />
              <p className="text-pretty text-sm text-muted-foreground">Селфи с проверкой живости, прислано с привязанного телефона. Сверьте с документом или с человеком на проходной.</p>
            </div>
            {dup && <p className="flex items-start gap-2 rounded-md bg-danger-soft px-3 py-2.5 text-sm text-danger-soft-foreground"><AlertTriangle className="mt-0.5 size-4 shrink-0" /><span>Снимок похож на эталон другого сотрудника — <Link to={routes.adminPerson(dup.id)} className="font-medium underline underline-offset-4">{dup.fullName}</Link>. Возможно, лицо подставили: подтверждайте только при личной сверке с документом.</span></p>}
            <div className="grid grid-cols-2 gap-2">
              <Button variant="danger-soft" disabled={busy} onClick={() => act(false)}><X />Отклонить</Button>
              <Button disabled={busy} onClick={() => act(true)}><CheckCircle2 />Подтвердить</Button>
            </div>
          </>
        ) : (
          <div className="flex items-center gap-3 text-sm">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-surface text-muted-foreground"><ScanFace className="size-4" /></span>
            <span className="text-pretty text-muted-foreground">{f.status === "ACTIVE" ? `Эталон ${FACE_TEXT[f.source ?? "HR"]}${f.at ? `, ${dateRu(f.at)}` : ""}` : f.status === "REJECTED" ? `Отклонено: ${f.comment}. Ждём новый снимок` : "Эталона нет: сотрудник добавит лицо в приложении после активации. До этого на «QR + лицо» пропустит только охранник"}</span>
          </div>
        )}
        <Button variant="secondary" size="sm" className="self-start" onClick={() => setShoot(true)}><Camera />{f.status === "ACTIVE" ? "Переснять эталон" : "Снять эталон сейчас"}</Button>
      </div>
      {shoot && <ShootDialog w={w} onClose={() => setShoot(false)} />}
    </Card>
  );
};

/** ADR-047: эталон при человеке — снимок проверяется сразу и заменяет прежний (в том числе селфи на проверке). */
const ShootDialog = ({ w, onClose: onClosed }: { w: Worker; onClose: () => void }) => {
  const { open, close: onClose } = useDialogState();
  const by = useSession((x) => x.userId);
  const [photo, setPhoto] = useState<string>();
  const [check, setCheck] = useState<FaceCheckState>({ status: "idle" });
  const [busy, setBusy] = useState(false);
  const ok = !!photo && check.status === "done" && check.result.ok;
  const save = async () => {
    if (!photo) return;
    setBusy(true);
    try { await api.setWorkerFace(w.id, photo, by); toast.success("Эталон сохранён — терминалы сверяют лицо с ним"); onClose(); }
    catch (e) { toast.error(e instanceof Error ? e.message : "Не удалось сохранить"); setBusy(false); }
  };
  return (
    <Dialog open={open} onClose={onClose} onClosed={onClosed} title="Эталон лица" description={`${w.fullName}. Снимайте только при человеке и сверьте лицо с документом`}
      footer={<><Button variant="quiet" onClick={onClose}>Отмена</Button><Button disabled={!ok || busy} onClick={save}><Check />Сделать эталоном</Button></>}>
      <FaceReferenceCapture value={photo} onChange={setPhoto} workerId={w.id} onCheck={setCheck} allowSkip={false} />
    </Dialog>
  );
};

/** Данные и допуски сотрудника. Зоны сгруппированы по объектам. */
const EditDialog = ({ w, onClose: onClosed }: { w: Worker; onClose: () => void }) => {
  const { open, close: onClose } = useDialogState();
  const db = useDb();
  const by = useSession((x) => x.userId);
  const [fullName, setFullName] = useState(w.fullName);
  const [position, setPosition] = useState(w.position);
  const [contractor, setContractor] = useState(w.contractor);
  const [zoneIds, setZoneIds] = useState(w.zoneIds);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const groups = useMemo(() => {
    const m = new Map<string, { name: string; zones: typeof db.zones }>();
    for (const z of db.zones) { const s = siteOfZone(db, z.id); const g = m.get(s.id) ?? { name: s.name, zones: [] }; g.zones.push(z); m.set(s.id, g); }
    return [...m.values()];
  }, [db]);
  const save = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true); setErr(null);
    try { await api.editWorker(w.id, { fullName, position, contractor, zoneIds }, by); toast.success("Карточка обновлена"); onClose(); }
    catch (x) { setErr(x instanceof Error ? x.message : "Не удалось сохранить"); setBusy(false); }
  };
  return (
    <Dialog open={open} onClose={onClose} onClosed={onClosed} title="Изменить карточку" description="Новые допуски действуют на терминалах сразу">
      <form onSubmit={save} className="flex flex-col gap-4">
        <Field label="ФИО"><Input value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="off" /></Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Должность"><Input value={position} onChange={(e) => setPosition(e.target.value)} /></Field>
          <Field label="Подрядчик"><Input value={contractor} onChange={(e) => setContractor(e.target.value)} /></Field>
        </div>
        <fieldset className="flex flex-col gap-3">
          <legend className="mb-1.5 text-sm font-medium">Допуск в зоны</legend>
          {groups.map((g) => (
            <div key={g.name} className="flex flex-col gap-2">
              <span className="text-xs font-medium text-muted-foreground">{g.name}</span>
              <div className="flex flex-wrap gap-2">
                {g.zones.map((z) => {
                  const on = zoneIds.includes(z.id);
                  return <Chip key={z.id} pressed={on} onClick={() => setZoneIds((v) => (on ? v.filter((x) => x !== z.id) : [...v, z.id]))}>{z.name}</Chip>;
                })}
              </div>
            </div>
          ))}
        </fieldset>
        {err && <p className="text-sm text-danger">{err}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="quiet" onClick={onClose}>Отмена</Button>
          <Button type="submit" disabled={busy || zoneIds.length === 0}>Сохранить</Button>
        </div>
      </form>
    </Dialog>
  );
};

export const PersonPage = () => {
  const { id = "" } = useParams();
  const db = useDb();
  const nav = useNavigate();
  const { role, userId } = useSession();
  const [invite, setInvite] = useState(false);
  const [edit, setEdit] = useState(false);
  const [remove, setRemove] = useState(false);
  const w = db.workers.find((x) => x.id === id);
  const inside = useMemo(() => presenceNow(db).some((p) => p.workerId === id), [db, id]);
  const worked = useMemo(() => workedMs(db, id, todayKey(), buildIntervals(db)), [db, id]);
  // Телефоны сотрудника — не в общем кэше: подгружаем при открытии карточки.
  useEffect(() => { if (id) api.loadDevices(id).catch(() => undefined); }, [id]);
  if (!w) return <Card><EmptyState title="Сотрудник не найден" action={<Link to={routes.adminPeople}><Button variant="outline">К списку</Button></Link>} /></Card>;
  const devices = db.devices.filter((d) => d.workerId === w.id).sort((a, b) => b.createdAt - a.createdAt);
  const history = db.attempts.filter((a) => a.workerId === w.id).slice(-20).reverse();
  const sh = shiftFor(db, w.id);

  return (
    <motion.div variants={stagger()} initial="hidden" animate="show">
      <motion.div variants={fadeUp}><Link to={routes.adminPeople} className="-ml-2 mb-4 inline-flex min-h-control-sm items-center gap-1.5 rounded-sm px-2 text-sm font-medium text-muted-foreground transition-colors duration-fast hover:bg-surface hover:text-foreground"><ArrowLeft className="size-4" />Люди</Link></motion.div>
      <motion.div variants={fadeUp} className="mb-5 flex flex-col gap-4 sm:mb-8 md:flex-row md:items-center">
        <div className="flex min-w-0 flex-1 items-center gap-4">
          <Avatar name={w.fullName} photo={w.photo} className="size-16 text-xl sm:size-20 sm:text-2xl" />
          <div className="min-w-0">
            <h1 className="text-balance font-display text-2xl font-semibold tracking-display sm:text-3xl">{w.fullName}</h1>
            <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-2 text-sm text-muted-foreground"><span>{w.position} · {w.contractor}</span><WorkerStatusBadge w={w} inside={inside} /></div>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:flex">
          <Button variant="secondary" onClick={() => setEdit(true)}><Pencil />Изменить</Button>
          <Button variant="secondary" onClick={async () => { if (!w.inviteCode) await api.regenerateInvite(w.id); setInvite(true); }}><QrCode />Приглашение</Button>
          {w.status === "active"
            ? <Button variant="danger-soft" onClick={() => api.updateWorker(w.id, { status: "blocked" }).then(() => toast.info("Доступ заблокирован"))}><Ban />Заблокировать</Button>
            : <Button onClick={() => api.updateWorker(w.id, { status: "active" }).then(() => toast.success("Доступ восстановлен"))}><CheckCircle2 />Разблокировать</Button>}
          {can(role, "manageObjects") && <Button variant="quiet" onClick={() => setRemove(true)}><UserX />Удалить</Button>}
        </div>
      </motion.div>
      <div className="grid gap-3 sm:gap-4 lg:grid-cols-3">
        <motion.div variants={fadeUp} className="min-w-0 lg:col-span-2">
          <Card className="h-full">
            <CardHeader className="flex-wrap"><CardTitle>Проходы</CardTitle><span className="text-sm text-muted-foreground">сегодня отработано <span className="font-medium tabular-nums text-foreground">{durationRu(worked)}</span></span></CardHeader>
            <div className="px-4 pb-2 pt-2 sm:px-6">{history.length ? <div className="divide-y divide-border">{history.map((a) => <AttemptRow key={a.id} a={a} showDate dirSource={directionSource(db.checkpoints.find((c) => c.id === a.checkpointId))} />)}</div> : <EmptyState title="Проходов не было" />}</div>
          </Card>
        </motion.div>
        <div className="flex min-w-0 flex-col gap-3 sm:gap-4">
          <motion.div variants={fadeUp}>
            <Card>
              <CardHeader><CardTitle>Допуск</CardTitle><Button variant="quiet" size="icon-sm" aria-label="Изменить допуск" onClick={() => setEdit(true)}><Pencil /></Button></CardHeader>
              <div className="flex flex-col gap-4 p-4 sm:p-6">
                <div className="flex flex-wrap gap-2">{w.zoneIds.map((z) => <Status key={z} tone="success">{db.zones.find((x) => x.id === z)?.name}</Status>)}</div>
                <Field label="Инструктаж и медосмотр до"><DatePicker value={w.permitUntil} onChange={(v) => api.updateWorker(w.id, { permitUntil: v })} /></Field>
                <div className="flex items-center justify-between gap-3 rounded-md bg-muted px-3 py-2.5 text-sm"><span className="text-muted-foreground">Смена сегодня</span><Link to={routes.adminShifts} className="font-medium tabular-nums underline-offset-4 hover:underline">{sh ? `${sh.start}–${sh.end}` : "нет · назначить"}</Link></div>
              </div>
            </Card>
          </motion.div>
          <motion.div variants={fadeUp}><FaceCard id={w.id} /></motion.div>
          <motion.div variants={fadeUp}>
            <Card>
              <CardHeader><CardTitle>Устройства</CardTitle></CardHeader>
              <div className="flex flex-col gap-1 p-2 sm:p-3">
                {devices.length === 0 && <div className="px-2 py-3 text-sm text-muted-foreground">Пропуск ещё не активирован</div>}
                {devices.map((d) => (
                  <div key={d.id} className="flex min-w-0 items-center gap-3 rounded-md px-2 py-2 text-sm">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-surface text-muted-foreground"><Smartphone className="size-4" /></span>
                    <div className="min-w-0 flex-1"><div className="truncate font-medium">{d.label}</div><div className="truncate text-xs text-muted-foreground">{dateRu(d.createdAt)} · {d.id}</div></div>
                    {d.revokedAt ? <Status>отвязан</Status> : <Button variant="quiet" size="icon-sm" aria-label="Отвязать" onClick={() => api.revokeDevice(d.id).then(() => toast.info("Телефон отвязан — его QR больше не пройдёт"))}><Trash2 /></Button>}
                  </div>
                ))}
              </div>
            </Card>
          </motion.div>
        </div>
      </div>
      {edit && <EditDialog w={w} onClose={() => setEdit(false)} />}
      <Dialog open={remove} onClose={() => setRemove(false)} title="Удалить сотрудника?" description={`${w.fullName}: телефоны отвяжутся, будущие смены снимутся, эталон лица удалится. Проходы в журнале и табель останутся. Временно закрыть доступ — кнопка «Заблокировать».`}
        footer={<><Button variant="quiet" onClick={() => setRemove(false)}>Отмена</Button><Button variant="danger" onClick={() => api.deleteWorker(w.id, userId).then(() => { toast.success("Сотрудник удалён"); nav(routes.adminPeople); }, (e) => { toast.error(e instanceof Error ? e.message : "Не удалось удалить"); setRemove(false); })}><Trash2 />Удалить</Button></>}>
        <span />
      </Dialog>
      <Dialog open={invite} onClose={() => setInvite(false)} title="Приглашение" description="Отсканируйте камерой смартфона, чтобы активировать пропуск сотрудника"><InviteCard w={w} /></Dialog>
    </motion.div>
  );
};
