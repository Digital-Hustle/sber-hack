import { useEffect, useState } from "react";
import { Link } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { History, Maximize2, Plus, Sparkles, X } from "lucide-react";
import { Button, LogoMark } from "@/shared/ui";
import { spring } from "@/shared/config/motion";
import { useChatHistory } from "../model/history";
import { AssistantChat } from "./AssistantChat";
import { ChatHistory } from "./ChatHistory";

/** Плавающий помощник в админке: мини-чат с историей, общей со страницей «Помощник». */
export const AssistantFab = ({ fullPath }: { fullPath: string }) => {
  const [open, setOpen] = useState(false);
  const [hist, setHist] = useState(false);
  const newSession = useChatHistory((s) => s.newSession);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);
  return (
    <>
      <AnimatePresence>
        {!open && (
          <motion.div initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.6 }} transition={spring.snappy} className="fixed bottom-24 right-4 z-overlay lg:bottom-6 lg:right-6">
            <Button variant="brand" size="icon-lg" aria-label="Открыть помощника" onClick={() => setOpen(true)} className="size-14 rounded-full shadow-float"><Sparkles className="size-6" /></Button>
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {open && (
          <motion.div role="dialog" aria-label="Помощник" initial={{ opacity: 0, y: 24, scale: 0.96 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 24, scale: 0.96 }} transition={spring.soft}
            className="fixed inset-x-3 bottom-3 top-20 z-modal flex origin-bottom-right flex-col overflow-hidden rounded-xl bg-card text-card-foreground shadow-pop ring-1 ring-border sm:inset-x-auto sm:bottom-6 sm:right-6 sm:top-auto sm:h-chat sm:w-full sm:max-w-md">
            <div className="flex items-center gap-2 border-b border-border px-3 py-2.5">
              <LogoMark className="size-8" />
              <div className="min-w-0 flex-1 truncate text-sm font-semibold">{hist ? "История" : "Помощник"}</div>
              <Button size="icon-sm" variant={hist ? "secondary" : "quiet"} aria-label="История диалогов" aria-pressed={hist} onClick={() => setHist((v) => !v)}><History /></Button>
              <Button size="icon-sm" variant="quiet" aria-label="Новый диалог" onClick={() => { newSession(); setHist(false); }}><Plus /></Button>
              <Link to={fullPath} tabIndex={-1} onClick={() => setOpen(false)}><Button size="icon-sm" variant="quiet" aria-label="Открыть целиком"><Maximize2 /></Button></Link>
              <Button size="icon-sm" variant="quiet" aria-label="Закрыть" onClick={() => setOpen(false)}><X /></Button>
            </div>
            <AnimatePresence mode="wait" initial={false}>
              <motion.div key={hist ? "h" : "c"} initial={{ opacity: 0, x: hist ? -16 : 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }} transition={spring.soft} className="flex min-h-0 flex-1 flex-col">
                {hist ? <ChatHistory onPick={() => setHist(false)} className="flex-1 p-3" /> : <AssistantChat compact className="min-h-0 flex-1" />}
              </motion.div>
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
};
