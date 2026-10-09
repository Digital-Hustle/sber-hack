import { AnimatePresence, motion } from "motion/react";
import { Check } from "lucide-react";
import { cn } from "@/shared/lib";
import { duration, ease, popIn, spring, tween } from "@/shared/config/motion";

export const TOOL_LABEL: Record<string, string> = {
  on_site_now: "Смотрю, кто на объекте",
  late_today: "Сверяю приходы со сменами",
  refusals: "Разбираю отказы на проходной",
  hours_worked: "Считаю отработанные часы",
  worker_today: "Ищу сотрудника в журнале",
};

/** Текст с бегущим бликом — «модель думает». Две ключевые точки, бесконечный линейный проход. */
export const Shimmer = ({ children, className }: { children: React.ReactNode; className?: string }) => (
  <motion.span className={cn("bg-linear-to-r from-muted-foreground via-foreground to-muted-foreground bg-clip-text text-transparent", className)}
    style={{ backgroundSize: "200% 100%" }} animate={{ backgroundPosition: ["100% 0%", "-100% 0%"] }}
    transition={{ duration: duration.loop * 0.75, ease: ease.linear, repeat: Infinity }}>{children}</motion.span>
);

export type Live = { tools: string[]; chunks: string[]; done?: boolean };

/**
 * Ответ, который ещё пишется: шаги-инструменты (активный — с бликом, пройденные — с галочкой),
 * затем текст — каждый пришедший кусок проявляется из размытия, в конце мигает курсор.
 */
export const StreamReply = ({ live }: { live: Live }) => {
  const writing = live.chunks.length > 0;
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-3">
      <ul className="flex flex-col gap-1.5">
        <AnimatePresence initial={false}>
          {(live.tools.length ? live.tools : writing ? [] : ["__think"]).map((t, i, all) => {
            const active = i === all.length - 1 && !writing;
            return (
              <motion.li key={t + i} layout initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.base }}
                className="flex items-center gap-2 text-sm">
                <span className="flex size-5 shrink-0 items-center justify-center">
                  {active ? <motion.span className="size-2 rounded-full bg-brand" animate={{ scale: [1, 1.5], opacity: [1, 0.35] }} transition={{ duration: duration.slow * 1.6, ease: ease.inOut, repeat: Infinity, repeatType: "reverse" }} />
                    : <motion.span variants={popIn} initial="hidden" animate="show" className="flex size-4 items-center justify-center rounded-full bg-accent text-accent-foreground"><Check className="size-3" strokeWidth={3} /></motion.span>}
                </span>
                {active ? <Shimmer>{t === "__think" ? "Думаю над вопросом…" : `${TOOL_LABEL[t] ?? `Вызываю ${t}`}…`}</Shimmer>
                  : <span className="text-muted-foreground">{TOOL_LABEL[t] ?? t}</span>}
              </motion.li>
            );
          })}
        </AnimatePresence>
      </ul>
      {writing && (
        <p className="whitespace-pre-wrap text-base leading-relaxed">
          {live.chunks.map((c, i) => (
            <motion.span key={i} initial={{ opacity: 0, filter: "blur(6px)" }} animate={{ opacity: 1, filter: "blur(0px)" }} transition={{ duration: duration.slow, ease: ease.out }}>{c}</motion.span>
          ))}
          {!live.done && <motion.span aria-hidden className="ml-0.5 inline-block size-2.5 translate-y-px rounded-full bg-brand-gradient align-baseline"
            animate={{ opacity: [1, 0.2] }} transition={{ duration: duration.slow, ease: ease.inOut, repeat: Infinity, repeatType: "reverse" }} />}
        </p>
      )}
    </div>
  );
};
