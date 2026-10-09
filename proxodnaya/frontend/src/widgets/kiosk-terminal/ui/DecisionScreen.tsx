import { useEffect } from "react";
import { createPortal } from "react-dom";
import { motion } from "motion/react";
import type { DecisionResult } from "@/shared/api";
import { viewForResult } from "@/entities/pass";
import { Avatar } from "@/shared/ui";
import { cn, hhmm, sound } from "@/shared/lib";
import { spring, tween, fadeUp, stagger } from "@/shared/config/motion";
import { motion as m } from "@/shared/config/tokens";

/** Экран вердикта: читается с 3 метров, текст — как пришёл с сервера, автосброс. Глубокий тон, не кислотный. */
export const DecisionScreen = ({ result, onDone }: { result: DecisionResult; onDone: () => void }) => {
  const v = viewForResult(result);
  const repeat = result.code === "REPEAT_SCAN";
  const Icon = v.icon;
  const hold = result.decision === "ALLOW" || repeat ? m.kiosk.resultHoldMs : m.kiosk.resultHoldMs * 1.5;
  useEffect(() => {
    (repeat ? sound.tick : result.decision === "ALLOW" || result.decision === "MANUAL" ? sound.allow : sound.deny)();
    const id = setTimeout(onDone, hold);
    return () => clearTimeout(id);
  }, [result, onDone, hold, repeat]);

  return createPortal(
    <motion.button type="button" onClick={onDone} aria-live="assertive"
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={tween.base}
      className={cn("fixed inset-0 z-kiosk flex flex-col items-center justify-center overflow-hidden px-6 pb-20 pt-10 text-center", v.bg, v.fg)}>
      <motion.div variants={stagger(0.08)} initial="hidden" animate="show" className="flex w-full max-w-4xl flex-col items-center gap-6 sm:gap-8">
        <div className="relative flex items-center justify-center">
          <motion.span className="absolute size-full rounded-full bg-white/15" initial={{ scale: 0.6, opacity: 0.8 }} animate={{ scale: 2.2, opacity: 0 }} transition={{ ...spring.soft, opacity: tween.base }} />
          <motion.span initial={{ scale: 0.3, rotate: -25, opacity: 0 }} animate={{ scale: 1, rotate: 0, opacity: 1 }} transition={{ ...spring.pop, opacity: tween.fast }}
            className="flex size-28 items-center justify-center rounded-full bg-white/15 sm:size-40">
            <Icon className="size-16 sm:size-24" strokeWidth={1.75} />
          </motion.span>
        </div>
        <motion.div variants={fadeUp} className="flex flex-col items-center gap-3">
          <div className="text-balance font-display text-4xl font-semibold tracking-display sm:text-6xl">{result.message}</div>
          <div className="max-w-3xl text-balance text-lg opacity-85 sm:text-2xl">{result.hint}</div>
        </motion.div>
        {result.worker && (
          <motion.div variants={fadeUp} className="flex max-w-full items-center gap-4 rounded-xl bg-black/15 py-3 pl-3 pr-6 text-left">
            <Avatar name={result.worker.fullName} photo={result.worker.photo} className="size-14 text-lg sm:size-16" />
            <div className="min-w-0">
              <div className="truncate text-lg font-medium sm:text-xl">{result.worker.fullName}</div>
              <div className="text-sm opacity-80 sm:text-base">{result.worker.position} · {result.decision === "ALLOW" || result.decision === "MANUAL" ? (result.direction === "IN" ? "вход" : "выход") : repeat ? "повторный скан" : "попытка"} в {hhmm(result.ts)}{result.score ? ` · сходство ${Math.round(result.score * 100)}%` : ""}</div>
            </div>
          </motion.div>
        )}
      </motion.div>
      <div className="absolute inset-x-6 bottom-6 flex flex-col items-center gap-3 sm:inset-x-10 sm:bottom-8">
        <div className="h-1 w-full max-w-md overflow-hidden rounded-full bg-black/15">
          {/* таймер автосброса: линейный — это часы, а не анимация */}
          <motion.div className="h-full origin-left rounded-full bg-white/80" initial={{ scaleX: 1 }} animate={{ scaleX: 0 }} transition={{ duration: hold / 1000, ease: "linear" }} />
        </div>
        <div className="text-sm opacity-70">{result.code} · коснитесь, чтобы продолжить</div>
      </div>
    </motion.button>,
    document.body,
  );
};
