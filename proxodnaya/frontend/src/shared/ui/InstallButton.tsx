import { useState, useSyncExternalStore } from "react";
import { Download, Share, SquarePlus, EllipsisVertical, Check } from "lucide-react";
import { installState, promptInstall, manualInstall, type InstallHow } from "@/shared/lib";
import { Button } from "./Button";
import { Dialog } from "./Dialog";

const STEPS: Record<InstallHow, { icon: typeof Share; text: React.ReactNode }[]> = {
  ios: [
    { icon: Share, text: <>Нажмите «Поделиться» внизу экрана Safari</> },
    { icon: SquarePlus, text: <>Выберите «На экран „Домой“»</> },
    { icon: Check, text: <>Нажмите «Добавить» — значок появится на экране телефона</> },
  ],
  "ios-other": [
    { icon: Share, text: <>Нажмите «Поделиться» в адресной строке или в меню браузера</> },
    { icon: SquarePlus, text: <>Выберите «На экран „Домой“». Если пункта нет — откройте страницу в Safari</> },
    { icon: Check, text: <>Нажмите «Добавить» — значок появится на экране телефона</> },
  ],
  "mac-safari": [
    { icon: Share, text: <>В меню Safari откройте «Файл»</> },
    { icon: SquarePlus, text: <>Выберите «Добавить в Dock»</> },
    { icon: Check, text: <>Приложение откроется отдельным окном</> },
  ],
  android: [
    { icon: EllipsisVertical, text: <>Откройте меню браузера</> },
    { icon: SquarePlus, text: <>Выберите «Установить» или «Добавить на главный экран»</> },
    { icon: Check, text: <>Подтвердите — значок появится на экране телефона</> },
  ],
};

/**
 * Кнопка «Установить». Если браузер умеет ставить приложение сам — показывает системный запрос,
 * если нет (Safari на iPhone и Mac, Firefox на Android) — короткую инструкцию. После установки скрывается.
 */
export const InstallButton = ({ label = "Установить", className, variant = "secondary", size = "sm" }: { label?: string; className?: string; variant?: "secondary" | "primary" | "brand"; size?: "sm" | "md" | "lg" }) => {
  const can = useSyncExternalStore(installState.subscribe, installState.canInstall, () => false);
  const [how] = useState(() => (typeof navigator === "undefined" ? null : manualInstall()));
  const [open, setOpen] = useState(false);
  if (installState.installed() || (!can && !how)) return null;
  return (
    <>
      <Button variant={variant} size={size} className={className} onClick={() => (can ? promptInstall() : setOpen(true))}><Download />{label}</Button>
      {how && (
        <Dialog open={open} onClose={() => setOpen(false)} title="Установить на экран" description="Приложение будет открываться с экрана как обычное, без адресной строки, и работать без сети"
          footer={<Button onClick={() => setOpen(false)}>Понятно</Button>}>
          <ol className="flex flex-col gap-3">
            {STEPS[how].map(({ icon: Icon, text }, i) => (
              <li key={i} className="flex items-center gap-3 rounded-lg bg-muted p-3 text-sm">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-card text-foreground shadow-xs"><Icon className="size-4" /></span>
                <span className="text-pretty">{text}</span>
              </li>
            ))}
          </ol>
        </Dialog>
      )}
    </>
  );
};
