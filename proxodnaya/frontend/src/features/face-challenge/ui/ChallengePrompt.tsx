import { motion } from "motion/react";
import { ArrowLeft, ArrowRight, Eye, ArrowDown } from "lucide-react";
import type { Challenge } from "@/shared/api";
import { Progress } from "@/shared/ui";
import { popIn, duration, ease } from "@/shared/config/motion";

const ICON = { "turn-left": ArrowLeft, "turn-right": ArrowRight, blink: Eye, nod: ArrowDown };
const SWAY = { "turn-left": { x: [0, -18, 0] }, "turn-right": { x: [0, 18, 0] }, blink: { scaleY: [1, 0.15, 1] }, nod: { y: [0, 14, 0] } };
const loop = { duration: duration.loop / 1.5, repeat: Infinity, ease: ease.inOut };

/** Подсказка челленджа живости поверх видео. */
export const ChallengePrompt = ({ challenge, name, progress }: { challenge: Challenge; name: string; progress: number }) => {
  const Icon = ICON[challenge.kind];
  return (
    <motion.div variants={popIn} initial="hidden" animate="show"
      className="flex w-full max-w-sm flex-col items-center gap-4 rounded-xl bg-popover/95 px-6 py-6 text-center text-popover-foreground shadow-pop backdrop-blur-md sm:gap-5 sm:px-8 sm:py-8">
      <div className="text-sm text-muted-foreground sm:text-base">{name ? `${name}, посмотрите в камеру` : "Посмотрите в камеру"}</div>
      <motion.div animate={SWAY[challenge.kind]} transition={loop} className="flex size-16 items-center justify-center rounded-full bg-accent text-accent-foreground sm:size-20">
        <Icon className="size-8 sm:size-10" />
      </motion.div>
      <div className="text-balance font-display text-2xl font-semibold tracking-display sm:text-3xl">{challenge.text}</div>
      <Progress value={progress} className="h-2 w-full" />
    </motion.div>
  );
};
