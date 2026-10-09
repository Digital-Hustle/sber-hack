import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { MessageSquare, Plus, Trash2 } from "lucide-react";
import { Button, Segmented } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { spring } from "@/shared/config/motion";
import { useChatHistory, type ChatSession } from "../model/history";

const dayLabel = (ts: number) => {
  const d = new Date(ts); d.setHours(0, 0, 0, 0);
  const t = new Date(); t.setHours(0, 0, 0, 0);
  const diff = Math.round((t.getTime() - d.getTime()) / 86400000);
  return diff === 0 ? "Сегодня" : diff === 1 ? "Вчера" : d.toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
};

/** История диалогов: по дням или по темам. */
export const ChatHistory = ({ onPick, className }: { onPick?: () => void; className?: string }) => {
  const { sessions, activeId, select, remove, newSession } = useChatHistory();
  const [by, setBy] = useState<"day" | "topic">("day");
  const groups = new Map<string, ChatSession[]>();
  [...sessions].sort((a, b) => b.updatedAt - a.updatedAt).forEach((s) => {
    const k = by === "day" ? dayLabel(s.updatedAt) : s.topic;
    groups.set(k, [...(groups.get(k) ?? []), s]);
  });
  return (
    <div className={cn("flex min-h-0 flex-col gap-3", className)}>
      <Button variant="secondary" onClick={() => { newSession(); onPick?.(); }}><Plus />Новый диалог</Button>
      <Segmented value={by} onChange={setBy} block size="sm" label="Группировка" options={[{ value: "day", label: "По дням" }, { value: "topic", label: "По темам" }]} />
      <div className="-mx-1 flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-1">
        {sessions.length === 0 && <p className="px-2 py-6 text-center text-sm text-muted-foreground">Здесь появятся ваши диалоги</p>}
        {[...groups].map(([g, list]) => (
          <div key={g} className="flex flex-col gap-0.5">
            <div className="px-2 pb-1 text-xs font-medium text-muted-foreground">{g}</div>
            <AnimatePresence initial={false}>
              {list.map((s) => (
                <motion.div key={s.id} layout initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={spring.soft}
                  className={cn("group flex items-center gap-1 rounded-md", s.id === activeId ? "bg-accent text-accent-foreground" : "hover:bg-surface")}>
                  <button type="button" onClick={() => { select(s.id); onPick?.(); }} className="flex min-h-control-md min-w-0 flex-1 items-center gap-2 rounded-md px-2 text-left text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring">
                    <MessageSquare className="size-4 shrink-0 opacity-60" /><span className="truncate">{s.title}</span>
                  </button>
                  <Button size="icon-sm" variant="quiet" aria-label="Удалить диалог" className="opacity-0 group-hover:opacity-100 focus-visible:opacity-100" onClick={() => remove(s.id)}><Trash2 /></Button>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        ))}
      </div>
    </div>
  );
};
