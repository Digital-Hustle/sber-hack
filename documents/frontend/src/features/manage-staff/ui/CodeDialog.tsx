import { useState } from "react";
import { Check, Copy, Smartphone } from "lucide-react";
import type { Enrollment } from "@/entities/staff";
import { Button, Dialog, StyledQr, toast } from "@/shared/ui";

/** Выданный код: QR для приложения-аутентификатора. Показывается один раз — закрыли окно, и секрета больше нигде нет. */
export const CodeDialog = ({ enrollment, onClose }: { enrollment: Enrollment | null; onClose: () => void }) => {
  const [copied, setCopied] = useState(false);
  const [shown, setShown] = useState<Enrollment | null>(null);
  if (enrollment && enrollment !== shown) { setShown(enrollment); setCopied(false); }
  const e = enrollment ?? shown;
  const copy = async () => {
    if (!e) return;
    try { await navigator.clipboard.writeText(e.secret); setCopied(true); } catch { toast.error("Не удалось скопировать — перепишите ключ вручную"); }
  };
  return (
    <Dialog open={!!enrollment} onClose={onClose} onClosed={() => setShown(null)} title={e ? `Код для ${e.login}` : "Код"}
      description="Покажите экран сотруднику: он сканирует QR приложением-аутентификатором. Больше этот QR не покажется — потеряют, выдайте новый"
      footer={<Button block size="lg" onClick={onClose}><Check />Готово, отсканировано</Button>}>
      {e && (
        <div className="flex flex-col items-center gap-4">
          <div className="rounded-2xl bg-white p-4 shadow-card"><StyledQr value={e.otpauthUri} className="size-56" label={`QR-код входа для ${e.login}`} /></div>
          <ol className="flex w-full flex-col gap-2 text-sm text-muted-foreground">
            <li className="flex gap-2"><Smartphone className="mt-0.5 size-4 shrink-0 text-brand" />Google Authenticator, Яндекс Ключ, Microsoft Authenticator или FreeOTP → «Добавить» → «Сканировать QR»</li>
            <li>Вход: логин <span className="font-medium text-foreground">{e.login}</span> и 6 цифр из приложения</li>
          </ol>
          <div className="flex w-full items-center gap-2 rounded-lg bg-surface p-2 pl-3">
            <code className="min-w-0 flex-1 truncate font-mono text-xs">{e.secret}</code>
            <Button size="sm" variant="quiet" onClick={() => void copy()}>{copied ? <Check /> : <Copy />}{copied ? "Скопирован" : "Ключ"}</Button>
          </div>
        </div>
      )}
    </Dialog>
  );
};
