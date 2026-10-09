// Пресеты анимаций «Субсидий». Все числа — из ./tokens (ADR-033); здесь только сборка.
// Правило: всё, что двигается, — пружина с отскоком (spring.*); твины только для цвета/прозрачности/таймеров.
import type { Transition, Variants } from "motion/react";
import { motion as m } from "./tokens";

export const ease = m.ease;
export const duration = m.duration;
export const spring = m.spring;
export const swipe = m.swipe;

/** Твины — только opacity/цвет: transition={tween.fast} */
export const tween = {
  instant: { duration: m.duration.instant, ease: m.ease.out },
  fast: { duration: m.duration.fast, ease: m.ease.out },
  base: { duration: m.duration.base, ease: m.ease.out },
  exit: { duration: m.duration.fast, ease: m.ease.in },
} as const satisfies Record<string, Transition>;

/** Появление снизу: y и scale — пружиной, прозрачность — твином (без «желейной» прозрачности). */
export const fadeUp: Variants = {
  hidden: { opacity: 0, y: m.distance.enter },
  show: { opacity: 1, y: 0, transition: { ...m.spring.soft, opacity: tween.base } },
  exit: { opacity: 0, y: -m.distance.exit, transition: tween.exit },
};

export const popIn: Variants = {
  hidden: { opacity: 0, scale: m.scale.popIn },
  show: { opacity: 1, scale: 1, transition: { ...m.spring.pop, opacity: tween.fast } },
  exit: { opacity: 0, scale: m.scale.popOut, transition: tween.exit },
};

export const stagger = (step: number = m.stagger.step, delay = 0): Variants => ({
  hidden: {},
  show: { transition: { staggerChildren: Math.min(step, m.stagger.max), delayChildren: delay } },
});

/** Нажатие для кликабельных элементов: <motion.button {...press}> */
export const press = {
  whileTap: { scale: m.scale.tap },
  transition: m.spring.press,
} as const;

/** Нажатие + лёгкий подъём для плиток/карточек-ссылок */
export const lift = {
  whileHover: { y: -3 },
  whileTap: { scale: m.scale.tap },
  transition: m.spring.press,
} as const;

/** Смена страницы */
export const pageIn = {
  initial: { opacity: 0, y: m.distance.enter },
  animate: { opacity: 1, y: 0 },
  transition: { ...m.spring.page, opacity: tween.base },
} as const;

/** Появление при прокрутке */
export const inView = {
  initial: "hidden",
  whileInView: "show",
  viewport: { once: true, amount: 0.25 },
  variants: fadeUp,
} as const;

/** Прочие числовые токены движения (пороги, скорости) */
export const motionTokens = { header: m.header, aurora: m.aurora, kioskMotion: m.kiosk.motion } as const;
