import { useEffect, useRef, useState } from "react";
import { motionTokens } from "@/shared/config/motion";

/**
 * Детектор движения по видео: сравнивает яркость соседних кадров в маленьком canvas (дёшево даже на слабом планшете).
 * Возвращает true, пока есть движение, и ещё holdMs после него. Кадры никуда не отправляются.
 */
export const useMotionDetect = (videoRef: React.RefObject<HTMLVideoElement | null>, enabled: boolean) => {
  const [active, setActive] = useState(false);
  const last = useRef(0);
  useEffect(() => {
    if (!enabled) { setActive(false); return; }
    const { sampleMs, holdMs, pixelDelta, ratio, width, height } = motionTokens.kioskMotion;
    const c = document.createElement("canvas");
    c.width = width; c.height = height;
    const ctx = c.getContext("2d", { willReadFrequently: true });
    let prev: Uint8ClampedArray | null = null;
    const id = setInterval(() => {
      const v = videoRef.current;
      if (!ctx || !v || v.readyState < 2) return;
      ctx.drawImage(v, 0, 0, width, height);
      const data = ctx.getImageData(0, 0, width, height).data;
      const luma = new Uint8ClampedArray(width * height);
      for (let i = 0; i < luma.length; i++) luma[i] = (data[i * 4] * 3 + data[i * 4 + 1] * 6 + data[i * 4 + 2]) / 10;
      if (prev) {
        let changed = 0;
        for (let i = 0; i < luma.length; i++) if (Math.abs(luma[i] - prev[i]) > pixelDelta) changed++;
        if (changed / luma.length > ratio) last.current = Date.now();
      }
      prev = luma;
      setActive(Date.now() - last.current < holdMs);
    }, sampleMs);
    return () => clearInterval(id);
  }, [enabled, videoRef]);
  return active;
};
