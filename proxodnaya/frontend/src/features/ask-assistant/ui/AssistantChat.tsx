import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ArrowUp, Check, Square, Users, AlarmClock, ShieldAlert, Timer, UserSearch } from "lucide-react";
import { assistant, type AssistantAnswer } from "@/shared/api";
import { Badge, Button, LogoMark } from "@/shared/ui";
import { StreamReply, TOOL_LABEL, type Live } from "./StreamReply";
import { cn } from "@/shared/lib";
import { fadeUp, press, spring, stagger, tween } from "@/shared/config/motion";

import { useChatHistory, type Msg } from "../model/history";

/** Ответ помощника не раздувает чат: в таблице не больше 30 строк, остальное — в разделе. */
const TABLE_MAX = 30;

const EMPTY: Msg[] = [];

const SUGGEST = [
  { q: "Кто сейчас на объекте?", icon: Users },
  { q: "Кто опоздал сегодня?", icon: AlarmClock },
  { q: "Почему были отказы за неделю?", icon: ShieldAlert },
  { q: "Сколько часов отработано за неделю?", icon: Timer },
  { q: "Что с Ивановым сегодня?", icon: UserSearch },
];

const Table = ({ t }: { t: NonNullable<AssistantAnswer["table"]> }) => (
  <div className="max-h-72 overflow-auto rounded-md bg-card ring-1 ring-border">
    <table className="w-full text-sm">
      <thead className="sticky top-0 bg-muted text-left text-muted-foreground"><tr>{t.columns.map((c) => <th key={c} className="whitespace-nowrap px-3 py-2 font-medium">{c}</th>)}</tr></thead>
      <tbody>{t.rows.slice(0, TABLE_MAX).map((r, i) => <tr key={i} className="border-t border-border">{r.map((c, j) => <td key={j} className="px-3 py-2 tabular-nums">{c}</td>)}</tr>)}</tbody>{t.rows.length > TABLE_MAX && <tfoot><tr className="border-t border-border"><td colSpan={t.columns.length} className="px-3 py-2 text-muted-foreground">Ещё {t.rows.length - TABLE_MAX} строк — уточните вопрос или откройте раздел целиком</td></tr></tfoot>}
    </table>
  </div>
);

/** Чат помощника: лента сообщений по центру, поле ввода — плавающая пилюля внизу. Цифры — только из данных. */
export const AssistantChat = ({ className, compact }: { className?: string; compact?: boolean }) => {
  const msgs = useChatHistory((s) => s.sessions.find((x) => x.id === s.activeId)?.msgs ?? EMPTY);
  const setMsgs = useChatHistory((s) => s.update);
  const [q, setQ] = useState("");
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState<Live | null>(null);
  const abort = useRef<AbortController | null>(null);
  const scroller = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  useEffect(() => { const el = scroller.current; if (msgs.length) el?.scrollTo({ top: el.scrollHeight, behavior: "smooth" }); }, [msgs, busy]);
  // Пока ответ пишется, лента «прилипает» к низу, только если пользователь сам не отмотал вверх.
  useEffect(() => { const el = scroller.current; if (el && live && el.scrollHeight - el.scrollTop - el.clientHeight < 160) el.scrollTop = el.scrollHeight; }, [live]);

  const send = async (text = q) => {
    const t = text.trim();
    if (!t || busy) return;
    setQ("");
    const history = msgs.map((m) => ({ role: m.role, text: m.text }));
    setMsgs((m) => [...m, { id: Date.now(), role: "user", text: t }]);
    setBusy(true);
    const ctl = new AbortController();
    abort.current = ctl;
    let acc: Live = { tools: [], chunks: [] };
    setLive(acc);
    try {
      const a = await assistant.ask(t, history, {
        signal: ctl.signal,
        onEvent: (e) => { acc = e.type === "tool" ? { ...acc, tools: [...acc.tools, e.name] } : { ...acc, chunks: [...acc.chunks, e.text] }; setLive(acc); },
      });
      setMsgs((m) => [...m, { id: Date.now() + 1, role: "assistant", text: a.text, answer: a }]);
    } catch (e) {
      const stopped = e instanceof DOMException && e.name === "AbortError";
      const partial = acc.chunks.join("");
      setMsgs((m) => [...m, stopped ? { id: Date.now() + 1, role: "assistant", text: partial ? `${partial.trimEnd()}…\n\nОтвет остановлен.` : "Ответ остановлен.", error: false }
        : { id: Date.now() + 1, role: "assistant", text: e instanceof Error ? e.message : "Не удалось получить ответ", error: true }]);
    } finally { setLive(null); setBusy(false); abort.current = null; input.current?.focus(); }
  };

  return (
    <div className={cn("flex min-h-0 flex-col", className)}>
      {!compact && <div className="flex items-center gap-3 border-b border-border px-4 py-3 sm:px-5">
        <LogoMark className="size-9" />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold">Помощник руководителя</div>
          <div className="truncate text-xs text-muted-foreground">Отвечает по данным системы</div>
        </div>
      </div>}

      <div ref={scroller} className="flex-1 overflow-y-auto px-4 py-6 sm:px-6">
        {msgs.length === 0 ? (
          <motion.div variants={stagger(0.05)} initial="hidden" animate="show" className="mx-auto flex min-h-full max-w-3xl flex-col justify-center gap-6">
            <motion.div variants={fadeUp} className="flex flex-col gap-2">
              <h2 className="font-display text-2xl font-semibold tracking-display sm:text-3xl">Чем помочь?</h2>
              <p className={cn("hidden text-base text-muted-foreground", !compact && "sm:block")}>Задайте вопрос об обстановке, опозданиях, отказах или отработанных часах. Каждый ответ сопровождается таблицей и источниками.</p>
            </motion.div>
            <div className={cn("grid gap-2", !compact && "sm:grid-cols-2")}>
              {SUGGEST.map(({ q: s, icon: Icon }, i) => (
                <motion.button key={s} type="button" variants={fadeUp} {...press} onClick={() => send(s)}
                  className={cn("flex min-h-control-lg items-center gap-3 rounded-md bg-muted px-3.5 py-3 text-left text-sm font-medium outline-none transition-colors duration-fast hover:bg-surface focus-visible:ring-2 focus-visible:ring-ring", i > 2 && !compact && "hidden sm:flex")}>
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-sm bg-card text-brand shadow-xs"><Icon className="size-4" /></span>{s}
                </motion.button>
              ))}
            </div>
          </motion.div>
        ) : (
          <div className="mx-auto flex max-w-3xl flex-col gap-5">
            <AnimatePresence initial={false}>
              {msgs.map((m) => (
                <motion.div key={m.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={spring.soft}
                  className={cn("flex gap-3", m.role === "user" ? "justify-end" : "justify-start")}>
                  {m.role === "user" ? (
                    <div className="ml-10 max-w-md whitespace-pre-wrap rounded-lg rounded-br-xs bg-primary px-4 py-2.5 text-base text-primary-foreground">{m.text}</div>
                  ) : (<>
                    <LogoMark className="mt-0.5 hidden size-8 sm:flex" />
                    <div className="flex min-w-0 flex-1 flex-col gap-3">
                      {m.answer && m.answer.tools.length > 0 && (
                        <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
                          {[...new Set(m.answer.tools)].map((t) => <span key={t} className="inline-flex items-center gap-1"><Check className="size-3 text-brand" strokeWidth={3} />{TOOL_LABEL[t] ?? t}</span>)}
                        </div>
                      )}
                      <p className={cn("whitespace-pre-wrap text-base leading-relaxed", m.error && "text-destructive")}>{m.text}</p>
                      {m.answer?.table && m.answer.table.rows.length > 0 && <Table t={m.answer.table} />}
                      {m.answer && m.answer.sources.length > 0 && (
                        <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">Источники:{m.answer.sources.map((s) => <Badge key={s}>{s}</Badge>)}</div>
                      )}
                    </div>
                  </>)}
                </motion.div>
              ))}
            </AnimatePresence>
            {live && (
              <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ ...spring.soft, opacity: tween.base }} className="flex gap-3" aria-live="polite">
                <LogoMark className="mt-0.5 hidden size-8 sm:flex" />
                <StreamReply live={live} />
              </motion.div>
            )}
          </div>
        )}
      </div>

      <div className="px-3 pb-3 sm:px-5 sm:pb-5">
        <form onSubmit={(e) => { e.preventDefault(); send(); }}
          className="mx-auto flex max-w-3xl items-end gap-2 rounded-lg bg-muted p-1.5 pl-4 ring-1 ring-border transition-shadow duration-fast focus-within:ring-2 focus-within:ring-ring">
          <textarea ref={input} rows={1} value={q} onChange={(e) => setQ(e.target.value)} aria-label="Вопрос помощнику"
            onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } }}
            placeholder="Задайте вопрос…"
            className="field-sizing-content max-h-40 min-h-control-md flex-1 resize-none bg-transparent py-2.5 text-base outline-none placeholder:text-subtle-foreground" />
          {busy ? <Button type="button" size="icon" variant="secondary" aria-label="Остановить ответ" className="rounded-md" onClick={() => abort.current?.abort()}><Square className="fill-current" /></Button>
            : <Button type="submit" size="icon" variant="brand" disabled={!q.trim()} aria-label="Отправить" className="rounded-md"><ArrowUp /></Button>}
        </form>
      </div>
    </div>
  );
};
