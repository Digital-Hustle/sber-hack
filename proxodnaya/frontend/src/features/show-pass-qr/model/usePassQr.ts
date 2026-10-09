import { useEffect, useState } from "react";
import { buildPassQr, currentWindow, windowEndsAt, QR_WINDOW_SEC, loadKey, type StoredKey } from "@/shared/lib";

/** QR пропуска: новая подпись каждые 30 с, без сети (ключ на устройстве). */
export const usePassQr = (slot = "phone") => {
  const [key, setKey] = useState<StoredKey | null | undefined>(undefined);
  const [qr, setQr] = useState<{ value: string; window: number } | null>(null);
  const [left, setLeft] = useState(QR_WINDOW_SEC);

  useEffect(() => { loadKey(slot).then((k) => setKey(k ?? null)).catch(() => setKey(null)); }, [slot]);

  useEffect(() => {
    if (!key) return;
    let alive = true;
    const tick = async () => {
      const w = currentWindow();
      setLeft(Math.max(0, (windowEndsAt(w) - Date.now()) / 1000));
      setQr((cur) => {
        if (cur?.window === w) return cur;
        buildPassQr(key, w).then((value) => alive && setQr({ value, window: w }));
        return cur;
      });
    };
    tick();
    const id = setInterval(tick, 250);
    return () => { alive = false; clearInterval(id); };
  }, [key]);

  return { key, qr, left, progress: left / QR_WINDOW_SEC };
};
