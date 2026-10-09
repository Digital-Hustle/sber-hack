import { useEffect, useRef } from "react";
import { BrowserQRCodeReader, type IScannerControls } from "@zxing/browser";

/** Распознаёт QR из уже запущенного <video>. Один и тот же текст не отдаёт чаще раза в 3 с. */
export const useQrScanner = (videoRef: React.RefObject<HTMLVideoElement | null>, enabled: boolean, onResult: (text: string) => void) => {
  const cb = useRef(onResult);
  cb.current = onResult;
  useEffect(() => {
    const video = videoRef.current;
    if (!enabled || !video) return;
    let controls: IScannerControls | undefined;
    let last = { text: "", at: 0 };
    let cancelled = false;
    const reader = new BrowserQRCodeReader(undefined, { delayBetweenScanAttempts: 120 });
    reader.decodeFromVideoElement(video, (res) => {
      if (!res || cancelled) return;
      const text = res.getText();
      if (text === last.text && Date.now() - last.at < 3000) return;
      last = { text, at: Date.now() };
      cb.current(text);
    }).then((c) => { controls = c; if (cancelled) c.stop(); }).catch(() => undefined);
    return () => { cancelled = true; controls?.stop(); };
  }, [videoRef, enabled]);
};
