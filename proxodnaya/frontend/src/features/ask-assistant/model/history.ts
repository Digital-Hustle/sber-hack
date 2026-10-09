import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AssistantAnswer } from "@/shared/api";

export type Msg = { id: number; role: "user" | "assistant"; text: string; answer?: AssistantAnswer; error?: boolean };
export type Topic = "Присутствие" | "Опоздания" | "Отказы" | "Часы" | "Сотрудник" | "Другое";
export type ChatSession = { id: string; title: string; topic: Topic; createdAt: number; updatedAt: number; msgs: Msg[] };

const RULES: [RegExp, Topic][] = [
  [/опозд|вовремя|рано/i, "Опоздания"],
  [/отказ|не пуст|денай|наруш/i, "Отказы"],
  [/(^|[^е])час|табел|отработ/i, "Часы"],
  [/кто сейчас|на объекте|внутри|присутст/i, "Присутствие"],
  [/что с |[А-ЯЁ][а-яё]+(ов|ев|ин|ский|ая)\b/, "Сотрудник"],
];
export const topicOf = (t: string): Topic => RULES.find(([re]) => re.test(t))?.[1] ?? "Другое";

type State = {
  sessions: ChatSession[];
  activeId: string | null;
  newSession: () => void;
  select: (id: string) => void;
  remove: (id: string) => void;
  /** Изменить сообщения активного диалога; если его нет — создаётся. */
  update: (fn: (m: Msg[]) => Msg[]) => void;
};

/** История диалогов с помощником: сохраняется между сессиями, общая для страницы и плавающего чата. */
export const useChatHistory = create<State>()(persist((set, get) => ({
  sessions: [],
  activeId: null,
  newSession: () => set({ activeId: null }),
  select: (activeId) => set({ activeId }),
  remove: (id) => set((s) => ({ sessions: s.sessions.filter((x) => x.id !== id), activeId: s.activeId === id ? null : s.activeId })),
  update: (fn) => {
    const { sessions, activeId } = get();
    const cur = sessions.find((x) => x.id === activeId);
    const now = Date.now();
    if (!cur) {
      const msgs = fn([]);
      const first = msgs.find((m) => m.role === "user")?.text ?? "Новый диалог";
      const s: ChatSession = { id: `c_${now.toString(36)}`, title: first.slice(0, 80), topic: topicOf(first), createdAt: now, updatedAt: now, msgs };
      set({ sessions: [s, ...sessions].slice(0, 40), activeId: s.id });
      return;
    }
    set({ sessions: sessions.map((x) => (x.id === cur.id ? { ...x, msgs: fn(x.msgs), updatedAt: now } : x)) });
  },
}), { name: "proxodnaya.assistant.history" }));
