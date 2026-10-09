import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AnimatePresence, motion } from "motion/react";
import { KeyRound, ShieldOff, UserCheck, UserPlus, Users } from "lucide-react";
import { useSession } from "@/entities/session";
import { STAFF_ROLE_TITLE, staffApi, staffKeys, type Enrollment, type StaffMember } from "@/entities/staff";
import { CodeDialog, CreateStaffDialog } from "@/features/manage-staff";
import { Avatar, Button, Card, Dialog, EmptyState, PageHeader, RowsSkeleton, Status, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { fadeUp, stagger } from "@/shared/config/motion";

type Confirm = { kind: "code" | "off" | "on"; m: StaffMember } | null;

/** FE-UI-03: сотрудники (старший). Вход только по коду: завести → QR; потерял телефон → новый код; ушёл → запретить вход. */
export const StaffPage = () => {
  const qc = useQueryClient();
  const { login } = useSession();
  const q = useQuery({ queryKey: staffKeys.all, queryFn: staffApi.list });
  const [create, setCreate] = useState(false);
  const [enrollment, setEnrollment] = useState<Enrollment | null>(null);
  const [confirm, setConfirm] = useState<Confirm>(null);
  const [last, setLast] = useState<Confirm>(null);
  if (confirm && confirm !== last) setLast(confirm);
  const c = confirm ?? last;

  const act = useMutation({
    mutationFn: async (x: NonNullable<Confirm>) => {
      if (x.kind === "code") return staffApi.reissue(x.m.login);
      await staffApi.setEnabled(x.m.login, x.kind === "on");
      return null;
    },
    onSuccess: (e, x) => {
      void qc.invalidateQueries({ queryKey: staffKeys.all });
      setConfirm(null);
      if (e) setEnrollment(e);
      else toast.success(x.kind === "on" ? `Вход для ${x.m.login} снова разрешён` : `Вход для ${x.m.login} запрещён, сессии закрыты`);
    },
    onError: (e) => toast.error(errorText(e)),
  });
  const items = q.data ?? [];

  return (
    <>
      <PageHeader title="Сотрудники" sub="Вход без паролей: логин и код из приложения. Код выдаёте вы — QR показывается один раз"
        actions={<Button variant="brand" onClick={() => setCreate(true)}><UserPlus />Новый сотрудник</Button>} />
      {q.isPending ? <Card className="overflow-hidden"><RowsSkeleton rows={4} /></Card> : q.isError ? (
        <EmptyState title="Список не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<Users />} title="Сотрудников нет" text="Заведите первого — он войдёт по коду из приложения" /></Card>
      ) : (
        <Card className="overflow-hidden">
          <motion.ul variants={stagger(0.03)} initial="hidden" animate="show">
            <AnimatePresence initial={false}>
              {items.map((m) => {
                const name = `${m.lastName} ${m.firstName}`.trim() || m.login;
                const self = m.login === login;
                return (
                  <motion.li key={m.login} layout="position" variants={fadeUp} exit="exit"
                    className="flex flex-col gap-3 border-t border-border px-4 py-3.5 first:border-t-0 sm:flex-row sm:items-center sm:gap-4 sm:px-6">
                    <div className="flex min-w-0 flex-1 items-center gap-3">
                      <Avatar name={name} className="size-10" />
                      <div className="min-w-0">
                        <div className="truncate font-medium">{name}{self && <span className="text-muted-foreground"> · это вы</span>}</div>
                        <div className="truncate text-xs text-muted-foreground">{m.login}</div>
                      </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-1.5">
                      <Status tone={m.role === "SENIOR_OPERATOR" ? "success" : m.role === "AUDITOR" ? "info" : "neutral"}>{STAFF_ROLE_TITLE[m.role]}</Status>
                      {!m.enabled ? <Status tone="danger">вход запрещён</Status> : !m.codeConfigured && <Status tone="warning">код не выдан</Status>}
                    </div>
                    <div className="flex shrink-0 gap-1.5">
                      <Button size="sm" variant="secondary" onClick={() => setConfirm({ kind: "code", m })}><KeyRound />{m.codeConfigured ? "Новый код" : "Выдать код"}</Button>
                      {!self && (m.enabled
                        ? <Button size="sm" variant="quiet" onClick={() => setConfirm({ kind: "off", m })}><ShieldOff />Запретить</Button>
                        : <Button size="sm" variant="quiet" onClick={() => setConfirm({ kind: "on", m })}><UserCheck />Разрешить</Button>)}
                    </div>
                  </motion.li>
                );
              })}
            </AnimatePresence>
          </motion.ul>
        </Card>
      )}

      <CreateStaffDialog open={create} onClose={() => setCreate(false)} onCreated={setEnrollment} />
      <CodeDialog enrollment={enrollment} onClose={() => setEnrollment(null)} />
      <Dialog open={!!confirm} onClose={() => setConfirm(null)} onClosed={() => setLast(null)}
        title={c?.kind === "code" ? `Новый код для ${c.m.login}?` : c?.kind === "off" ? `Запретить вход ${c?.m.login}?` : `Разрешить вход ${c?.m.login}?`}
        description={c?.kind === "code" ? "Старый код перестанет работать сразу, открытые сессии закроются. Новый QR покажем один раз"
          : c?.kind === "off" ? "Сотрудник выйдет на всех устройствах и не сможет войти, пока вы не разрешите снова" : "Сотрудник сможет войти своим кодом"}
        footer={<Button block size="lg" variant={c?.kind === "off" ? "danger" : "primary"} disabled={act.isPending} onClick={() => c && act.mutate(c)}>
          {act.isPending ? "Сохраняем…" : c?.kind === "code" ? "Выдать новый код" : c?.kind === "off" ? "Запретить вход" : "Разрешить вход"}</Button>}>
        {null}
      </Dialog>
    </>
  );
};
