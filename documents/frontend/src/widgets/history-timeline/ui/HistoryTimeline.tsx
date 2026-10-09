import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { History } from "lucide-react";
import { actionLabel, dateTimeRu, reviewApi, reviewKeys, sourceLabel } from "@/entities/application";
import { Button, EmptyState, RowsSkeleton } from "@/shared/ui";
import { fadeUp, stagger } from "@/shared/config/motion";

const detailsText = (d: unknown): string | null => {
  if (!d || typeof d !== "object") return null;
  const o = d as Record<string, unknown>;
  const parts: string[] = [];
  if (o.field) parts.push(String(o.title ?? o.field));
  if ("from" in o || "to" in o) parts.push(`${o.from ?? "—"} → ${o.to ?? "—"}`);
  if (o.decision) parts.push(String(o.decision) === "ACCEPTED" ? "принята" : "возвращена");
  if (o.type && !("to" in o)) parts.push(String(o.type));
  if (o.comment) parts.push(`«${o.comment}»`);
  if (o.format) parts.push(String(o.format).toUpperCase());
  if (Array.isArray(o.items)) parts.push(o.items.map((i: { reason?: string }) => `«${i.reason ?? ""}»`).join(", "));
  return parts.length ? parts.join(" · ") : null;
};

/** FE-OP-08 / B-08: история заявки — кто, что и когда (журнал append-only на сервере). */
export const HistoryTimeline = ({ appId }: { appId: string }) => {
  const q = useQuery({ queryKey: reviewKeys.history(appId), queryFn: () => reviewApi.history(appId) });
  if (q.isPending) return <RowsSkeleton rows={4} />;
  if (q.isError) return <EmptyState title="История не загрузилась" action={<Button size="sm" variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />;
  const items = q.data?.items ?? [];
  if (!items.length) return <EmptyState icon={<History />} title="Событий пока нет" />;
  return (
    <motion.ol variants={stagger(0.02)} initial="hidden" animate="show" className="relative ml-2 flex flex-col gap-3 border-l border-border pl-4">
      {items.map((h) => (
        <motion.li key={h.id} variants={fadeUp} className="relative">
          <span className="absolute -left-5 top-1.5 size-2 rounded-full bg-primary" />
          <div className="text-sm font-medium">{actionLabel(h.action)}</div>
          <div className="text-xs text-muted-foreground">{dateTimeRu(h.createdAt)} · {sourceLabel(h.source)}{h.actor ? ` ${h.actor}` : ""}</div>
          {detailsText(h.details) && <div className="mt-0.5 text-xs text-muted-foreground">{detailsText(h.details)}</div>}
        </motion.li>
      ))}
    </motion.ol>
  );
};
