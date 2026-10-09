import { useRef } from "react";
import { Camera, Upload } from "lucide-react";
import { Button, type ButtonProps } from "@/shared/ui";

const ACCEPT = "application/pdf,image/jpeg,image/png,image/heic,image/heif,image/tiff,image/webp";

/** Выбор файла и съёмка камерой телефона (capture=environment — нативная камера с автофокусом). */
export const UploadButtons = ({ onFile, disabled, label = "Загрузить", size = "md", camera = true, variant = "primary" }: {
  onFile: (f: File) => void; disabled?: boolean; label?: string; size?: ButtonProps["size"]; camera?: boolean; variant?: ButtonProps["variant"];
}) => {
  const pick = useRef<HTMLInputElement>(null);
  const shot = useRef<HTMLInputElement>(null);
  const take = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    e.target.value = "";
    if (f) onFile(f);
  };
  return (
    <div className="flex flex-wrap gap-2">
      <input ref={pick} type="file" accept={ACCEPT} hidden onChange={take} />
      <input ref={shot} type="file" accept="image/*" capture="environment" hidden onChange={take} />
      <Button size={size} variant={variant} disabled={disabled} onClick={() => pick.current?.click()}><Upload />{label}</Button>
      {camera && <Button size={size} variant="secondary" disabled={disabled} onClick={() => shot.current?.click()} aria-label="Снять камерой"><Camera /><span className="max-sm:hidden">Снять</span></Button>}
    </div>
  );
};
