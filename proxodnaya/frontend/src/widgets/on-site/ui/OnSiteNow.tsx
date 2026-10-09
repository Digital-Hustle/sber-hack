import { useMemo } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Link } from "react-router";
import { HardHat } from "lucide-react";
import { presenceNow, useDb } from "@/shared/api";
import { WorkerCell } from "@/entities/worker";
import { EmptyState } from "@/shared/ui";
import { durationRu } from "@/shared/lib";
import { useNow } from "@/shared/hooks";
import { routes } from "@/shared/const/router";
import { popIn } from "@/shared/config/motion";

const LIMIT = 12;

/** Превью «кто на объекте»: первые 12 по времени входа, полный список — в «Людях» с фильтром. */
export const OnSiteNow = () => {
  const db = useDb();
  const now = useNow(30000);
  const list = useMemo(() => presenceNow(db).sort((a, b) => a.since - b.since), [db]);
  if (!list.length) return <EmptyState icon={<HardHat />} title="На объекте никого" text="Сотрудники появятся здесь после прохода через киоск" />;
  const zones = new Map(db.zones.map((z) => [z.id, z.name]));
  const workers = new Map(db.workers.map((w) => [w.id, w]));
  return (
    <div className="flex flex-col gap-2">
    <div className="grid gap-1 sm:grid-cols-2">
      <AnimatePresence initial={false}>
        {list.slice(0, LIMIT).map((p) => {
          const w = workers.get(p.workerId);
          if (!w) return null;
          return (
            <motion.div key={p.workerId} layout variants={popIn} initial="hidden" animate="show" exit="exit">
              <Link to={routes.adminPerson(w.id)} className="block min-w-0 rounded-md p-2.5 transition-colors duration-fast hover:bg-muted">
                <WorkerCell w={w} sub={`${zones.get(p.zoneId) ?? "зона"} · ${durationRu(now - p.since)}`} />
              </Link>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
    {list.length > LIMIT && <Link to={`${routes.adminPeople}?f=inside`} className="self-start rounded-md px-2.5 py-1.5 text-sm font-medium text-muted-foreground transition-colors duration-fast hover:bg-muted hover:text-foreground">Все {list.length} на объекте →</Link>}
    </div>
  );
};
