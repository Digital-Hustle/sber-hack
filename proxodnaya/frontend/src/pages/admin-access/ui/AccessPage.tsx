import { useMemo, useState } from "react";
import { Link } from "react-router";
import { motion } from "motion/react";
import { KeyRound, Search, UserPlus } from "lucide-react";
import { useSession, roleLabel, ROLES } from "@/entities/session";
import { api, useDb, type AdminUser, type AccessEvent, type Role } from "@/shared/api";
import { Avatar, Button, Card, CardHeader, CardTitle, Dialog, EmptyState, Field, Input, PageHeader, Select, Status, toast } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { agoRu, dateRu, hhmm } from "@/shared/lib/time";
import { fadeUp, stagger } from "@/shared/config/motion";

const ROLE_OPTIONS = ROLES.map((r) => ({ value: r.id, label: r.label }));
const STATUS_ORDER: Record<AdminUser["status"], number> = { ACTIVE: 0, INVITED: 1, DISABLED: 2 };
const ACTION: Record<AccessEvent["action"], string> = { INVITE: "Приглашение", ROLE: "Смена роли", DISABLE: "Доступ отключён", ENABLE: "Доступ возвращён", JOIN: "Первый вход", CODE: "Смена кода терминалов", LOGIN: "Вход по коду из письма", LOGOUT: "Выход", OBJECT: "Объекты", WORKER: "Сотрудники" };
const msg = (e: unknown, f: string) => (e instanceof Error ? e.message : f);

const StatusMark = ({ u }: { u: AdminUser }) =>
  u.status === "ACTIVE" ? <Status tone="success">активен</Status>
  : u.status === "INVITED" ? <Status tone="info">приглашён</Status>
  : <Status tone="danger">отключён</Status>;

const InviteDialog = ({ open, onClose, byId }: { open: boolean; onClose: () => void; byId: string }) => {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>("GUARD");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setErr(null);
    try {
      const u = await api.inviteAdmin({ name, email, role }, byId);
      toast.success(`${u.name} приглашён — ${roleLabel(u.role)}`);
      setName(""); setEmail(""); setRole("GUARD"); onClose();
    } catch (x) { setErr(msg(x, "Не удалось пригласить")); } finally { setBusy(false); }
  };
  return (
    <Dialog open={open} onClose={onClose} title="Пригласить в панель" description="Человек получит письмо со ссылкой. Права появятся сразу после первого входа">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Имя и фамилия"><Input value={name} onChange={(e) => setName(e.target.value)} autoComplete="off" placeholder="Например, Анна Орлова" /></Field>
        <Field label="Рабочая почта"><Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" placeholder="a.orlova@proxodnaya.ru" /></Field>
        <Field label="Роль" hint={ROLES.find((r) => r.id === role)?.text} error={err}><Select value={role} onChange={setRole} options={ROLE_OPTIONS} /></Field>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="quiet" onClick={onClose}>Отмена</Button>
          <Button type="submit" disabled={busy}><UserPlus />Пригласить</Button>
        </div>
      </form>
    </Dialog>
  );
};

export const AccessPage = () => {
  const db = useDb();
  const userId = useSession((x) => x.userId);
  const admins = useMemo(() => db.admins ?? [], [db.admins]);
  const log = db.accessLog ?? [];
  const [q, setQ] = useState("");
  const [roleF, setRoleF] = useState<Role | "ALL">("ALL");
  const [invite, setInvite] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<AdminUser | null>(null);
  const nameOf = (id: string) => admins.find((u) => u.id === id)?.name ?? "—";

  const list = useMemo(() => {
    const s = q.trim().toLowerCase();
    return admins
      .filter((u) => (roleF === "ALL" || u.role === roleF) && (!s || u.name.toLowerCase().includes(s) || u.email.includes(s)))
      .sort((a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status] || a.name.localeCompare(b.name, "ru"));
  }, [admins, q, roleF]);

  const changeRole = async (u: AdminUser, role: Role) => {
    setBusy(u.id);
    try { await api.setAdminRole(u.id, role, userId); toast.success(`${u.name}: теперь ${roleLabel(role)}`); }
    catch (e) { toast.error(msg(e, "Не удалось сменить роль")); } finally { setBusy(null); }
  };
  const toggle = async (u: AdminUser) => {
    const enable = u.status === "DISABLED";
    setBusy(u.id); setConfirm(null);
    try { await api.setAdminActive(u.id, enable, userId); toast.success(enable ? `${u.name}: доступ возвращён` : `${u.name}: доступ отключён`); }
    catch (e) { toast.error(msg(e, "Не удалось изменить доступ")); } finally { setBusy(null); }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Доступ" sub="Кто работает в панели и что ему открыто. Изменения действуют сразу и попадают в журнал"
        actions={<Button onClick={() => setInvite(true)}><UserPlus />Пригласить</Button>} />
      <motion.div variants={stagger()} initial="hidden" animate="show" className="flex flex-col gap-6">
        <motion.div variants={fadeUp} className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {ROLES.map((r) => {
            const n = admins.filter((u) => u.role === r.id && u.status !== "DISABLED").length;
            const on = roleF === r.id;
            return (
              <button key={r.id} type="button" aria-pressed={on} onClick={() => setRoleF(on ? "ALL" : r.id)}
                className={`flex flex-col gap-1 rounded-lg border px-4 py-3 text-left outline-none transition-colors duration-fast focus-visible:ring-2 focus-visible:ring-ring ${on ? "border-primary bg-accent text-accent-foreground" : "border-border bg-card hover:bg-surface"}`}>
                <span className="font-display text-2xl font-semibold tabular-nums">{n}</span>
                <span className="text-sm leading-tight opacity-80">{r.label}</span>
              </button>
            );
          })}
        </motion.div>

        <motion.div variants={fadeUp}><Card>
          <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center sm:px-6">
            <Input icon={<Search />} value={q} onChange={(e) => setQ(e.target.value)} placeholder="Имя или почта" aria-label="Поиск" className="sm:flex-1" />
            <Select value={roleF} onChange={setRoleF} options={[{ value: "ALL" as const, label: "Все роли" }, ...ROLE_OPTIONS]} aria-label="Роль" className="sm:w-56" />
          </div>
          {list.length === 0 ? <EmptyState icon={<KeyRound />} title="Никого не нашли" text="Измените поиск или пригласите человека" /> : (
            <ul className="divide-y divide-border">
              {list.map((u) => {
                const self = u.id === userId;
                const off = u.status === "DISABLED";
                return (
                  <li key={u.id} className="flex flex-wrap items-center gap-3 px-4 py-3.5 sm:px-6">
                    <Avatar name={u.name} className={off ? "size-10 opacity-50" : "size-10"} />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2"><span className="truncate font-medium">{u.name}</span>{self && <Status tone="neutral">это вы</Status>}<StatusMark u={u} /></div>
                      <div className="truncate text-sm text-muted-foreground">{u.email} · {u.lastSeen ? `заходил ${agoRu(u.lastSeen)}` : "ещё не входил"}</div>
                    </div>
                    <div className="flex w-full items-center gap-2 sm:w-auto">
                      {self || off
                        ? <span className="flex-1 px-1 text-sm text-muted-foreground sm:w-56 sm:flex-none">{roleLabel(u.role)}</span>
                        : <Select value={u.role} onChange={(r) => changeRole(u, r)} options={ROLE_OPTIONS} aria-label={`Роль: ${u.name}`} className="flex-1 sm:w-56 sm:flex-none" />}
                      {!self && (
                        <Button variant={off ? "secondary" : "quiet"} size="sm" disabled={busy === u.id} onClick={() => (off ? toggle(u) : setConfirm(u))}>
                          {off ? "Вернуть" : "Отключить"}
                        </Button>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Card></motion.div>

        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Журнал изменений</CardTitle></CardHeader>
          {log.length === 0 ? <p className="p-4 text-sm text-muted-foreground sm:p-6">Пока изменений не было. Приглашения, смены ролей, отключения и правки объектов появятся здесь.</p> : (
            <ul className="divide-y divide-border">
              {log.slice(0, 30).map((e) => (
                <li key={e.id} className="flex flex-col gap-0.5 px-4 py-3 sm:flex-row sm:items-center sm:gap-4 sm:px-6">
                  <span className="shrink-0 text-sm tabular-nums text-muted-foreground sm:w-28">{dateRu(e.ts)}, {hhmm(e.ts)}</span>
                  <span className="min-w-0 flex-1 text-sm">
                    <span className="font-medium">{ACTION[e.action]}</span> · {e.action === "CODE" ? (e.code === "guard" ? "код охранника" : "сервисный код") : e.detail ?? nameOf(e.target)}
                    {e.action === "ROLE" && e.from && e.to && <span className="text-muted-foreground"> · {roleLabel(e.from)} → {roleLabel(e.to)}</span>}
                    {e.action === "INVITE" && e.to && <span className="text-muted-foreground"> · {roleLabel(e.to)}</span>}
                  </span>
                  {e.action !== "JOIN" && <span className="shrink-0 text-sm text-muted-foreground">{nameOf(e.by)}</span>}
                </li>
              ))}
            </ul>
          )}
        </Card></motion.div>

        <motion.p variants={fadeUp} className="text-pretty text-sm text-muted-foreground">
          Свою роль поменять нельзя, а последнего администратора нельзя понизить или отключить — так панель никогда не останется без управления.{" "}
          <Link to={routes.adminSettings} className="font-medium text-foreground underline-offset-4 hover:underline">Что открывает каждая роль</Link>
        </motion.p>
      </motion.div>

      <InviteDialog open={invite} onClose={() => setInvite(false)} byId={userId} />
      <Dialog open={!!confirm} onClose={() => setConfirm(null)} title="Отключить доступ?" description={confirm ? `${confirm.name} больше не сможет войти в панель. Вернуть доступ можно в любой момент.` : undefined}
        footer={<><Button variant="quiet" onClick={() => setConfirm(null)}>Отмена</Button><Button variant="danger" onClick={() => confirm && toggle(confirm)}>Отключить</Button></>}>
        <span />
      </Dialog>
    </div>
  );
};
