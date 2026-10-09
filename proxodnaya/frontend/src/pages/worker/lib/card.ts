import { useEffect } from "react";
import type { Worker } from "@/shared/api";

// ADR-043: карточка пропуска хранится на телефоне — без сети приложение открывается из кэша и показывает её вместе с QR.
const CARD = "proxodnaya.worker.cardView";
export type CardData = Pick<Worker, "id" | "fullName" | "position" | "contractor" | "photo" | "permitUntil">;

const read = (id: string): CardData | undefined => {
  try { const c = JSON.parse(localStorage.getItem(CARD) ?? "null") as CardData | null; return c?.id === id ? c : undefined; } catch { return undefined; }
};

/** Живые данные, если есть, иначе сохранённая на телефоне карточка. */
export const useWorkerCard = (live: Worker | undefined, id: string): CardData | undefined => {
  useEffect(() => {
    if (!live) return;
    const { fullName, position, contractor, photo, permitUntil } = live;
    try { localStorage.setItem(CARD, JSON.stringify({ id: live.id, fullName, position, contractor, photo, permitUntil })); } catch { /* фото может не влезть — карточка без него */ }
  }, [live]);
  return live ?? read(id);
};

export const forgetWorkerCard = () => localStorage.removeItem(CARD);
