import { useEffect, useState, type ReactNode } from "react";
import { motion, useReducedMotion } from "motion/react";
import { duration, ease } from "@/shared/config/motion";

// Один путь на всё: четыре кубических сегмента «лево → низ → право → верх → лево» (против часовой стрелки).
// Линия — те же сегменты, сплющенные в отрезок: первые два идут слева направо, вторые два возвращаются по нему же.
// Видна ровно половина пути (pathLength 0.5), поэтому отрезок, «чаша» и кольцо — одна фигура без подмены элементов,
// а нижняя и верхняя половины зеркальны: при морфинге видимая доля всегда остаётся точной.
const K = 16.57; // 30 · 0.5523 — контрольные точки дуги окружности радиуса 30
const line = (y: number) =>
  `M 14 ${y} C 14 ${y} 32 ${y} 50 ${y} C 68 ${y} 86 ${y} 86 ${y} C 86 ${y} 68 ${y} 50 ${y} C 32 ${y} 14 ${y} 14 ${y}`;
const RING = `M 20 50 C 20 ${50 + K} ${50 - K} 80 50 80 C ${50 + K} 80 80 ${50 + K} 80 50 C 80 ${50 - K} ${50 + K} 20 50 20 C ${50 - K} 20 20 ${50 - K} 20 50`;

// Внутренний знак: два штриха одной толщины с кольцом. Галочка → крестик → схлопывается в точку.
// Это часть заставки, а не вердикт: цвет белый, как у кольца.
const CHECK = "M 37 51 L 46 60 M 46 60 L 63 42";
const CROSS = "M 39 39 L 61 61 M 39 61 L 61 39";
const DOT = "M 50 50 L 50 50 M 50 50 L 50 50";
// Глаза смайлика: точки (нулевой отрезок с круглым концом), моргание — короткий штрих.
const EYES_UP = "M 39 31 L 39 31.01 M 61 31 L 61 31.01";
const EYES = "M 39 37 L 39 37.01 M 61 37 L 61 37.01";
const BLINK = "M 36 38 L 42 38 M 58 38 L 64 38";
const TOP = 16;
const BOTTOM = 84;

type T = { duration: number; ease: readonly [number, number, number, number]; delay?: number };
const glide: T = { duration: duration.loop / 2, ease: ease.inOut };
const settle: T = { duration: duration.slow * 1.4, ease: ease.inOut };
const bulge: T = { duration: duration.slow * 1.6, ease: ease.inOut };
const close: T = { duration: duration.slow * 2, ease: ease.inOut };
const draw: T = { duration: duration.slow * 1.3, ease: ease.out, delay: duration.fast };
const swap: T = { duration: duration.slow * 1.4, ease: ease.inOut, delay: duration.slow };
const fold: T = { duration: duration.slow, ease: ease.in, delay: duration.slow };
const pop: T = { duration: duration.slow, ease: ease.out, delay: duration.fast };
const blink: T = { duration: duration.instant, ease: ease.inOut, delay: duration.slow * 1.6 };
const unblink: T = { duration: duration.fast, ease: ease.out };
const smile: T = { duration: duration.slow * 2, ease: ease.inOut, delay: duration.slow * 1.4 };

/** len — видимая доля пути, off — сдвиг начала (0.5 — видна верхняя половина), e — глаза смайлика. */
type Step = { d: string; len: number; off: number; g: { d: string; len: number }; e?: { d: string; o: number }; t: T };
const NONE = { d: CHECK, len: 0 };
const NO_EYES = { d: EYES_UP, o: 0 };

/**
 * Цикл: линия сканирует рамку → встаёт в центр → низ выгибается в улыбку, над ней появляются глаза и моргают → дуга дорастает
 * против часовой стрелки до кольца → внутри пишется галочка, перетекает в крестик и схлопывается →
 * кольцо продолжает движение против часовой стрелки, раскрываясь сверху, и снова ложится в линию.
 */
const STEPS: Step[] = [
  { d: line(BOTTOM), len: 0.5, off: 0, g: NONE, t: glide },
  { d: line(TOP), len: 0.5, off: 0, g: NONE, t: glide },
  { d: line(BOTTOM), len: 0.5, off: 0, g: NONE, t: glide },
  { d: line(50), len: 0.5, off: 0, g: NONE, t: settle },
  { d: RING, len: 0.5, off: 0, g: NONE, t: bulge },
  { d: RING, len: 0.5, off: 0, g: NONE, e: { d: EYES, o: 1 }, t: pop },
  { d: RING, len: 0.5, off: 0, g: NONE, e: { d: BLINK, o: 1 }, t: blink },
  { d: RING, len: 0.5, off: 0, g: NONE, e: { d: EYES, o: 1 }, t: unblink },
  { d: RING, len: 1, off: 0, g: NONE, e: { d: EYES_UP, o: 0 }, t: smile },
  { d: RING, len: 1, off: 0, g: { d: CHECK, len: 1 }, t: draw },
  { d: RING, len: 1, off: 0, g: { d: CROSS, len: 1 }, t: swap },
  { d: RING, len: 1, off: 0, g: { d: DOT, len: 1 }, t: fold },
  { d: RING, len: 0.5, off: 0.5, g: { d: DOT, len: 0 }, t: close },
  { d: line(50), len: 0.5, off: 0.5, g: NONE, t: bulge },
  { d: line(TOP), len: 0.5, off: 0.5, g: NONE, t: settle },
];
const RING_FROM = 4;
const RING_TO = 12;

// Толщина в единицах viewBox (без non-scaling-stroke: с ним Chromium неверно считает штрихи по pathLength).
const stroke = { className: "stroke-white", strokeWidth: 1.7, strokeLinecap: "round", strokeLinejoin: "round" } as const;

/** Знак сканера для экрана «Покажите QR-пропуск». Без свечения и цвета: одна белая линия и знак той же толщины. */
export const ScanMark = ({ children }: { children?: ReactNode }) => {
  const reduce = useReducedMotion();
  const [i, setI] = useState(0);
  const step = STEPS[i];
  useEffect(() => {
    if (reduce) return;
    const t = setTimeout(() => setI((x) => (x + 1) % STEPS.length), (step.t.duration + (step.t.delay ?? 0)) * 1000);
    return () => clearTimeout(t);
  }, [i, reduce, step]);
  const ring = i >= RING_FROM && i <= RING_TO;
  // Сдвиг начала после раскрытия кольца сбрасывается мгновенно: в виде линии обе половины пути совпадают, скачка не видно.
  const offT = step.off === 0 && i === 0 ? { duration: 0 } : step.t;
  const glyphT = step.g.d === DOT && step.g.len === 0 ? { duration: duration.fast, ease: ease.out } : step.t;
  return (
    <>
      {children && (
        <motion.div className="absolute inset-0 flex items-center justify-center text-white/50" initial={false}
          animate={{ opacity: ring ? 0 : 1, scale: ring ? 0.9 : 1 }} transition={bulge}>{children}</motion.div>
      )}
      <svg aria-hidden viewBox="0 0 100 100" fill="none" className="absolute inset-0 size-full overflow-visible">
        {reduce ? <path d={line(50)} {...stroke} /> : (
          <motion.path {...stroke} initial={{ d: line(TOP), pathLength: 0.5, pathSpacing: 0.5, pathOffset: 0 }}
            animate={{ d: step.d, pathLength: step.len, pathSpacing: 1 - step.len, pathOffset: step.off }}
            transition={{ ...step.t, pathOffset: offT }} />
        )}
        {!reduce && (
          <motion.path {...stroke} strokeWidth={3.4} initial={{ d: EYES_UP, opacity: 0 }}
            animate={{ d: (step.e ?? NO_EYES).d, opacity: (step.e ?? NO_EYES).o }} transition={step.e ? step.t : { duration: 0 }} />
        )}
        {!reduce && (
          <motion.path {...stroke} initial={{ d: CHECK, pathLength: 0, opacity: 0 }}
            animate={{ d: step.g.d, pathLength: step.g.len, opacity: step.g.len ? 1 : 0 }} transition={glyphT} />
        )}
      </svg>
    </>
  );
};
