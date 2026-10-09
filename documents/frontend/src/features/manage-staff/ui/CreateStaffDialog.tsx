import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UserPlus } from "lucide-react";
import { STAFF_ROLE_TITLE, staffApi, staffKeys, type Enrollment, type StaffRole } from "@/entities/staff";
import { Button, Dialog, Field, Input, Segmented, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

const LOGIN = /^[a-z][a-z0-9._-]{2,31}$/;
const ROLES: StaffRole[] = ["OPERATOR", "SENIOR_OPERATOR", "AUDITOR"];

/** Завести сотрудника: пароля нет — сразу выдаётся код (QR). */
export const CreateStaffDialog = ({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (e: Enrollment) => void }) => {
  const qc = useQueryClient();
  const [login, setLogin] = useState("");
  const [first, setFirst] = useState("");
  const [last, setLast] = useState("");
  const [role, setRole] = useState<StaffRole>("OPERATOR");
  const l = login.trim().toLowerCase();
  const loginErr = l && !LOGIN.test(l) ? "3–32 символа: латиница, цифры, точка, дефис; начинается с буквы" : null;
  const m = useMutation({
    mutationFn: () => staffApi.create({ login: l, firstName: first.trim(), lastName: last.trim(), role }),
    onSuccess: (e) => {
      void qc.invalidateQueries({ queryKey: staffKeys.all });
      toast.success(`Сотрудник ${e.login} заведён`);
      onClose();
      onCreated(e);
    },
    onError: (e) => toast.error(errorText(e)),
  });
  const reset = () => { setLogin(""); setFirst(""); setLast(""); setRole("OPERATOR"); };
  const ready = l && !loginErr && first.trim() && last.trim();
  return (
    <Dialog open={open} onClose={onClose} onClosed={reset} title="Новый сотрудник" description="Пароля не будет: после создания покажем QR-код для приложения-аутентификатора"
      footer={<Button block size="lg" disabled={!ready || m.isPending} onClick={() => m.mutate()}><UserPlus />{m.isPending ? "Заводим…" : "Завести и выдать код"}</Button>}>
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (ready) m.mutate(); }}>
        <Segmented block value={role} onChange={setRole} label="Роль" options={ROLES.map((r) => ({ value: r, label: STAFF_ROLE_TITLE[r] }))} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Фамилия"><Input value={last} onChange={(e) => setLast(e.target.value)} maxLength={64} autoComplete="off" /></Field>
          <Field label="Имя"><Input value={first} onChange={(e) => setFirst(e.target.value)} maxLength={64} autoComplete="off" /></Field>
        </div>
        <Field label="Логин" error={loginErr} hint="Латиницей, например ivanov или a.petrova">
          <Input value={login} onChange={(e) => setLogin(e.target.value)} autoCapitalize="none" spellCheck={false} autoComplete="off" maxLength={32} />
        </Field>
        <button type="submit" hidden />
      </form>
    </Dialog>
  );
};
