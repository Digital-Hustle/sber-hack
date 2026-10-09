import { useState } from "react";
import { useNavigate } from "react-router";
import { useMutation } from "@tanstack/react-query";
import { ArrowRight, ShieldCheck } from "lucide-react";
import { applicantApi } from "@/entities/application";
import { Button, Field, Input, SwitchRow, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { applicationKey, contactError, parseContact } from "@/shared/lib";
import { routes } from "@/shared/const/router";

/** FE-AP-01: согласие → заявка → ключ во фрагменте ссылки и код возобновления (показываем один раз). */
export const StartForm = ({ onCreated }: { onCreated?: (r: { id: string; key: string; resumeCode?: string }) => void }) => {
  const nav = useNavigate();
  const [consent, setConsent] = useState(false);
  const [contact, setContact] = useState("");
  const [touched, setTouched] = useState(false);
  const parsed = parseContact(contact);
  const err = touched && !parsed ? contactError(contact) : null;
  const m = useMutation({
    mutationFn: () => applicantApi.create(parsed ?? {}),
    onSuccess: (r) => {
      if (!r.applicationId || !r.accessKey) return;
      applicationKey.set(r.applicationId, r.accessKey);
      onCreated?.({ id: r.applicationId, key: r.accessKey, resumeCode: r.resumeCode });
      nav(routes.applyApplication(r.applicationId), { state: { resumeCode: r.resumeCode, fresh: true } });
    },
    onError: (e) => toast.error(errorText(e)),
  });
  return (
    <form className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); setTouched(true); if (parsed && consent && !m.isPending) m.mutate(); }}>
      <Field label="Телефон или почта" hint="Нужны, чтобы вернуться к заявке с другого устройства по коду" error={err}>
        <Input value={contact} onChange={(e) => setContact(e.target.value)} onBlur={() => contact.trim() && setTouched(true)} aria-invalid={!!err} required placeholder="+7 900 000-00-00 или name@mail.ru" autoComplete="email" inputMode="email" maxLength={120} />
      </Field>
      <SwitchRow icon={<ShieldCheck />} title="Согласен на обработку персональных данных"
        text="Документы нужны только для оформления на работу. Их видит оператор кадровой службы" checked={consent} onChange={setConsent} />
      <Button type="submit" size="lg" block disabled={!consent || m.isPending}>{m.isPending ? "Создаём заявку…" : "Начать"}<ArrowRight /></Button>
    </form>
  );
};
