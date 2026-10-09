import type { WeekDayPlan } from "@/shared/api";

/** План недели: по дню на Пн…Вс. off — выходной. */
export type DayPlan = { on: boolean; start: string; end: string };
export type WeekPlan = DayPlan[];
export const WEEKDAYS = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
export const WEEKDAYS_FULL = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"];

export const weekPlan = (weekdays: number[], start: string, end: string): WeekPlan => WEEKDAYS.map((_, i) => ({ on: weekdays.includes(i), start, end }));
export const TEMPLATES: { label: string; plan: () => WeekPlan }[] = [
  { label: "Пятидневка 08:00–17:00", plan: () => weekPlan([0, 1, 2, 3, 4], "08:00", "17:00") },
  { label: "Шестидневка, сб короче", plan: () => weekPlan([0, 1, 2, 3, 4, 5], "08:00", "18:00").map((d, i) => (i === 5 ? { ...d, end: "14:00" } : d)) },
  { label: "Вечерняя 14:00–23:00", plan: () => weekPlan([0, 1, 2, 3, 4], "14:00", "23:00") },
];

export const planDays = (p: WeekPlan): WeekDayPlan[] => p.flatMap((d, weekday) => (d.on ? [{ weekday, start: d.start, end: d.end }] : []));
export const planValid = (p: WeekPlan) => p.some((d) => d.on) && p.every((d) => !d.on || d.start < d.end);
/** Все рабочие дни с одинаковым временем — тогда план показывается в режиме «Одинаково». */
export const planUniform = (p: WeekPlan) => { const on = p.filter((d) => d.on); return on.every((d) => d.start === on[0]?.start && d.end === on[0]?.end); };
