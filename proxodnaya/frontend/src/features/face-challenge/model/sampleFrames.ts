/** Сервис лиц принимает до 5 кадров на сверку (face/api/schemas.py) — больше не шлём. */
export const FRAMES_MAX = 5;
const MAX_SIDE = 640;
const QUALITY = 0.85;

/** Один кадр с камеры — JPEG data-URL, длинная сторона не больше 640 px. Нет картинки — null. */
export const grabFrame = (video: HTMLVideoElement | null, maxSide = MAX_SIDE): string | null => {
  if (!video || !video.videoWidth) return null;
  const k = Math.min(1, maxSide / Math.max(video.videoWidth, video.videoHeight));
  const c = document.createElement("canvas");
  c.width = Math.round(video.videoWidth * k);
  c.height = Math.round(video.videoHeight * k);
  const ctx = c.getContext("2d");
  if (!ctx) return null;
  ctx.drawImage(video, 0, 0, c.width, c.height);
  return c.toDataURL("image/jpeg", QUALITY);
};

/** Снимает count кадров равномерно за durationMs, пока человек выполняет челлендж. Живость оценивает сервер. */
export const sampleFrames = (video: HTMLVideoElement | null, durationMs: number, count = FRAMES_MAX): Promise<string[]> =>
  new Promise((resolve) => {
    const out: string[] = [];
    const step = durationMs / count;
    const id = setInterval(() => {
      const f = grabFrame(video);
      if (f) out.push(f);
      if (out.length >= count) { clearInterval(id); resolve(out); }
    }, step);
    setTimeout(() => { clearInterval(id); resolve(out); }, durationMs + step);
  });
