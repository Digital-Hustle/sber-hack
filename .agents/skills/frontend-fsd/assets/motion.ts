// Токены анимаций «Проходной». Кладётся в src/shared/config/motion.ts (во время хакатона).
// Все анимации только через motion/react (ADR-031). Длительности в секундах.
import type { Transition, Variants } from "motion/react";

export const ease = {
  out: [0.22, 1, 0.36, 1],       // вход элементов, раскрытие
  in: [0.4, 0, 1, 1],            // уход элементов (короче входа)
  inOut: [0.65, 0, 0.35, 1],     // перемещение между состояниями
  emphasized: [0.2, 0, 0, 1],    // крупные переходы экранов
} as const;

export const duration = {
  instant: 0.08,  // нажатие
  fast: 0.16,     // hover, тултип, чекбокс
  base: 0.24,     // поповер, селект, тост
  slow: 0.36,     // модалка, шторка, смена экрана
  hero: 0.6,      // только лендинг/презентация, не в пути прохода
} as const;

export const spring = {
  press: { type: "spring", stiffness: 600, damping: 30, mass: 0.6 },     // кнопки, свитчи
  snappy: { type: "spring", stiffness: 420, damping: 34 },              // поповеры, индикатор табов
  soft: { type: "spring", stiffness: 240, damping: 28 },                // карточки, layout
  sheet: { type: "spring", stiffness: 320, damping: 36, mass: 0.9 },    // bottom sheet, drawer
  bouncy: { type: "spring", bounce: 0.35, visualDuration: 0.45 },       // успех (галочка ALLOW)
} as const satisfies Record<string, Transition>;

export const fadeUp: Variants = {
  hidden: { opacity: 0, y: 12, filter: "blur(4px)" },
  show: { opacity: 1, y: 0, filter: "blur(0px)", transition: { duration: duration.slow, ease: ease.out } },
  exit: { opacity: 0, y: -8, transition: { duration: duration.fast, ease: ease.in } },
};

export const scaleIn: Variants = {
  hidden: { opacity: 0, scale: 0.96 },
  show: { opacity: 1, scale: 1, transition: spring.snappy },
  exit: { opacity: 0, scale: 0.98, transition: { duration: duration.fast, ease: ease.in } },
};

export const stagger = (step = 0.04, delay = 0): Variants => ({
  hidden: {},
  show: { transition: { staggerChildren: step, delayChildren: delay } },
});

/** Нажатие для любых кликабельных элементов: <motion.button {...press}> */
export const press = {
  whileHover: { scale: 1.02 },
  whileTap: { scale: 0.97 },
  transition: spring.press,
} as const;

/** Появление при прокрутке: <motion.section {...inView}> */
export const inView = {
  initial: "hidden",
  whileInView: "show",
  viewport: { once: true, amount: 0.3 },
  variants: fadeUp,
} as const;

/** Пороги свайпов (px и px/s) — одинаковые во всём приложении */
export const swipe = { offset: 80, velocity: 500, confirmRatio: 0.85 } as const;
