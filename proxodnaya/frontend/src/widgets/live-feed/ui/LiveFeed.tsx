import { AnimatePresence, motion } from "motion/react";
import { ScrollText } from "lucide-react";
import { Link } from "react-router";
import { useDb } from "@/shared/api";
import { routes } from "@/shared/const/router";
import { AttemptRow } from "@/entities/pass";
import { EmptyState } from "@/shared/ui";
import { spring, tween, duration } from "@/shared/config/motion";

/** Лента последних проходов; обновляется сама (вкладка с киоском → BroadcastChannel). Новая строка въезжает пружиной и подсвечивается. */
export const LiveFeed = ({ limit = 8 }: { limit?: number }) => {
  const db = useDb();
  const list = db.attempts.slice(-limit).reverse();
  const who = (id?: string) => db.workers.find((w) => w.id === id)?.fullName ?? "Неизвестный пропуск";
  if (!list.length) return <EmptyState icon={<ScrollText />} title="Проходов пока нет" text="Откройте киоск и покажите QR" />;
  return (
    <div className="flex flex-col">
      <AnimatePresence initial={false}>
        {list.map((a) => (
          <motion.div key={a.id} layout initial={{ opacity: 0, y: -16, backgroundColor: "var(--accent)" }} animate={{ opacity: 1, y: 0, backgroundColor: "var(--card)" }} exit={{ opacity: 0 }}
            transition={{ ...spring.soft, opacity: tween.fast, backgroundColor: { duration: duration.loop / 2 } }} className="border-t border-border first:border-t-0">
            <AttemptRow a={a} who={a.workerId ? <Link to={routes.adminPerson(a.workerId)} className="block truncate rounded-sm text-sm font-medium hover:text-accent-foreground hover:underline">{who(a.workerId)}</Link> : <div className="truncate text-sm font-medium">{who(a.workerId)}</div>} className="px-4 sm:px-6" />
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
};
