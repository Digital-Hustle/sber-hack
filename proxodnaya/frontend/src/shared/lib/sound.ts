// Короткие сигналы киоска на WebAudio — без файлов.
let ctx: AudioContext | null = null;

const beep = (freqs: number[], step = 0.12) => {
  try {
    ctx ??= new AudioContext();
    const t0 = ctx.currentTime;
    freqs.forEach((f, i) => {
      const o = ctx!.createOscillator();
      const g = ctx!.createGain();
      o.type = "sine";
      o.frequency.value = f;
      g.gain.setValueAtTime(0.0001, t0 + i * step);
      g.gain.exponentialRampToValueAtTime(0.25, t0 + i * step + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + (i + 1) * step);
      o.connect(g).connect(ctx!.destination);
      o.start(t0 + i * step);
      o.stop(t0 + (i + 1) * step + 0.02);
    });
  } catch { /* звук не критичен */ }
};

export const sound = {
  allow: () => beep([660, 990]),
  deny: () => beep([300, 220], 0.18),
  tick: () => beep([880], 0.05),
};
