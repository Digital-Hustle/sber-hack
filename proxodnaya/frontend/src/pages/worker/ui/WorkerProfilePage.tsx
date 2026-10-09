import { useMemo, useState } from "react";
import { Link, useNavigate, useOutletContext } from "react-router";
import { motion } from "motion/react";
import { Building2, ChevronRight, KeyRound, LogOut, ScanFace, Smartphone, WifiOff } from "lucide-react";
import { api, useDb, presenceNow, workerSites, siteOfZone, forgetCard, PHONE_SLOT } from "@/shared/api";
import { Avatar, Button, Card, CardHeader, CardTitle, Dialog, InstallButton, PageHeader, Status, ThemePicker, toast } from "@/shared/ui";
import { cn, deleteKey } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { fadeUp, stagger } from "@/shared/config/motion";
import type { WorkerCtx } from "@/widgets/worker-shell";
import { forgetWorkerCard, useWorkerCard } from "../lib/card";

const dateLong = (v: string | number) => new Date(typeof v === "string" ? `${v}T12:00:00` : v).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" });
const FACE_LABEL = { NONE: <Status tone="warning">не добавлено</Status>, PENDING: <Status tone="info">на проверке</Status>, REJECTED: <Status tone="danger">переснять</Status>, ACTIVE: <Status tone="success">подтверждено</Status> } as const;

const Row = ({ icon, k, v }: { icon: React.ReactNode; k: string; v: React.ReactNode }) => (
  <div className="flex min-w-0 items-center gap-3 px-4 py-3 sm:px-6">
    <span className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-surface text-muted-foreground [&_svg]:size-4">{icon}</span>
    <span className="min-w-0 flex-1 text-sm text-muted-foreground">{k}</span>
    <span className="min-w-0 truncate text-right text-sm font-medium">{v}</span>
  </div>
);

/** Профиль сотрудника: кто я, где у меня допуск, этот телефон, оформление и выход. */
export const WorkerProfilePage = () => {
  const { key } = useOutletContext<WorkerCtx>();
  const db = useDb();
  const faceLabel = FACE_LABEL[db.workers.find((x) => x.id === key.workerId)?.face?.status ?? "ACTIVE"];
  const nav = useNavigate();
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const w = useWorkerCard(db.workers.find((x) => x.id === key.workerId), key.workerId);
  const sites = useMemo(() => workerSites(db, key.workerId), [db, key.workerId]);
  const pres = useMemo(() => presenceNow(db).find((p) => p.workerId === key.workerId), [db, key.workerId]);
  const here = pres ? siteOfZone(db, pres.zoneId) : undefined;
  const device = db.devices.find((d) => d.id === key.deviceId);
  const live = db.workers.find((x) => x.id === key.workerId);

  const signOut = async () => {
    setBusy(true);
    try {
      // Сервер отзывает ключ этого телефона, закрытая часть удаляется — старые QR больше не пройдут.
      await api.unbindMyDevice();
      await deleteKey(PHONE_SLOT);
      forgetCard(); forgetWorkerCard();
      toast.info("Пропуск на этом телефоне отключён");
      nav(routes.workerActivate, { replace: true });
    } finally { setBusy(false); }
  };

  if (!w) return null;
  return (
    <div>
      <PageHeader kicker="Профиль" title={w.fullName} sub={`${w.position} · ${w.contractor}`} />
      <motion.div variants={stagger(0.06)} initial="hidden" animate="show" className="flex flex-col gap-3 sm:gap-4">
        <motion.div variants={fadeUp}>
          <Card className="flex items-center gap-4 p-4 sm:p-6">
            <Avatar name={w.fullName} photo={w.photo} className="size-16 text-lg" />
            <div className="min-w-0 flex-1">
              <div className="text-xs text-muted-foreground">Допуск к работам до</div>
              <div className="font-display text-xl font-semibold tabular-nums tracking-display">{dateLong(w.permitUntil)}</div>
            </div>
            {live?.status === "blocked" ? <Status tone="danger">Заблокирован</Status> : <Status tone="success">Активен</Status>}
          </Card>
        </motion.div>

        {/* ADR-044: один код на все объекты — терминал каждого объекта сам проверяет допуск к своим зонам */}
        <motion.div variants={fadeUp}>
          <Card>
            <CardHeader><CardTitle>Мои объекты</CardTitle><Status tone="success">{sites.length}</Status></CardHeader>
            <ul className="flex flex-col gap-1 p-2 sm:p-3">
              {sites.map(({ site, zones }) => {
                const on = here?.id === site.id;
                return (
                  <li key={site.id} className={cn("flex min-w-0 items-center gap-3 rounded-md p-2.5", on && "bg-accent")}>
                    <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-sm", on ? "bg-brand text-white" : "bg-surface text-muted-foreground")}><Building2 className="size-5" /></span>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium">{site.name}</div>
                      <div className="truncate text-xs text-muted-foreground">{zones.map((z) => z.name).join(" · ")}{site.address ? ` · ${site.address}` : ""}</div>
                    </div>
                    {on && <Status tone="success">Вы здесь</Status>}
                  </li>
                );
              })}
            </ul>
            <p className="border-t border-border px-4 py-3 text-xs text-muted-foreground sm:px-6">Один QR на все объекты: терминал на проходной сам проверит допуск к своим зонам. Переключать ничего не нужно.</p>
          </Card>
        </motion.div>

        <motion.div variants={fadeUp}>
          <Card>
            <CardHeader><CardTitle>Этот телефон</CardTitle><InstallButton /></CardHeader>
            <div className="flex flex-col divide-y divide-border pt-2">
              <Row icon={<Smartphone />} k="Устройство" v={device?.label ?? "Телефон"} />
              <Row icon={<KeyRound />} k="Ключ создан" v={dateLong(key.createdAt)} />
              <Row icon={<WifiOff />} k="Без сети" v="QR работает" />
              <Link to={routes.workerFace} className="outline-none transition-colors duration-fast hover:bg-surface focus-visible:bg-surface">
                <Row icon={<ScanFace />} k="Лицо для прохода" v={<span className="inline-flex items-center gap-1">{faceLabel}<ChevronRight className="size-4 text-subtle-foreground" /></span>} />
              </Link>
            </div>
          </Card>
        </motion.div>

        <motion.div variants={fadeUp}>
          <Card>
            <CardHeader><CardTitle>Оформление</CardTitle></CardHeader>
            <div className="p-4 sm:p-6"><ThemePicker /></div>
          </Card>
        </motion.div>

        <motion.div variants={fadeUp}>
          <Button variant="danger-soft" size="lg" block onClick={() => setConfirm(true)}><LogOut />Выйти с этого телефона</Button>
        </motion.div>
      </motion.div>

      <Dialog open={confirm} onClose={() => setConfirm(false)} title="Выйти с этого телефона?" description="Пропуск на телефоне отключится, ключ удалится. Чтобы вернуться, понадобится новый код приглашения от руководителя.">
        <div className="grid gap-2 sm:grid-cols-2">
          <Button variant="secondary" onClick={() => setConfirm(false)}>Отмена</Button>
          <Button variant="danger" disabled={busy} onClick={signOut}><LogOut />Выйти</Button>
        </div>
      </Dialog>
    </div>
  );
};
