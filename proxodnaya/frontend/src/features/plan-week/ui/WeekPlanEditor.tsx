import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { CopyCheck } from "lucide-react";
import { Chip, Field, Segmented, Switch, TimePicker } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { press, fadeUp, spring, tween } from "@/shared/config/motion";
import { TEMPLATES, WEEKDAYS, WEEKDAYS_FULL, planUniform, type WeekPlan } from "../model/plan";

/**
 * Редактор графика недели (ADR-046): «Одинаково» — дни недели и одно время на все, «По дням» — у каждого дня
 * свой выход и время, можно разнести время одного дня на все рабочие. Шаблоны заполняют план целиком.
 */
export const WeekPlanEditor = ({ value, onChange }: { value: WeekPlan; onChange: (p: WeekPlan) => void }) => {
  const [mode, setMode] = useState<"same" | "days">(() => (planUniform(value) ? "same" : "days"));
  const first = value.find((d) => d.on) ?? value[0];
  const setDay = (i: number, patch: Partial<WeekPlan[number]>) => onChange(value.map((d, j) => (j === i ? { ...d, ...patch } : d)));
  const setAll = (patch: Partial<WeekPlan[number]>) => onChange(value.map((d) => ({ ...d, ...patch })));
  const toAll = (i: number) => onChange(value.map((d) => (d.on ? { ...d, start: value[i].start, end: value[i].end } : d)));

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2">
        {TEMPLATES.map((t) => (
          <Chip key={t.label} onClick={() => { const p = t.plan(); onChange(p); setMode(planUniform(p) ? "same" : "days"); }}>{t.label}</Chip>
        ))}
      </div>
      <Segmented block label="Как задать время" value={mode} onChange={(m) => { if (m === "same") setAll({ start: first.start, end: first.end }); setMode(m); }}
        options={[{ value: "same", label: "Одинаково все дни" }, { value: "days", label: "По дням" }]} />

      <AnimatePresence mode="popLayout" initial={false}>
        {mode === "same" ? (
          <motion.div key="same" variants={fadeUp} initial="hidden" animate="show" exit={{ opacity: 0, transition: tween.exit }} className="flex flex-col gap-4">
            <Field label="Дни недели">
              <div className="grid grid-cols-7 gap-1.5" role="group">
                {WEEKDAYS.map((d, i) => (
                  <motion.button key={d} type="button" {...press} aria-pressed={value[i].on} aria-label={WEEKDAYS_FULL[i]} onClick={() => setDay(i, { on: !value[i].on, start: first.start, end: first.end })}
                    className={cn("h-control-sm rounded-md border text-sm font-medium transition-colors duration-fast", value[i].on ? "border-primary bg-primary text-primary-foreground" : "border-border hover:bg-surface", i > 4 && !value[i].on && "text-muted-foreground")}>{d}</motion.button>
                ))}
              </div>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Начало"><TimePicker value={first.start} onChange={(v) => setAll({ start: v })} /></Field>
              <Field label="Конец" error={first.start >= first.end ? "Конец смены должен быть позже начала" : null}><TimePicker value={first.end} onChange={(v) => setAll({ end: v })} /></Field>
            </div>
          </motion.div>
        ) : (
          <motion.ul key="days" variants={fadeUp} initial="hidden" animate="show" exit={{ opacity: 0, transition: tween.exit }} className="flex flex-col divide-y divide-border rounded-lg border border-border">
            {value.map((d, i) => (
              <motion.li key={i} layout transition={spring.soft} className="flex flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2.5">
                <div className="flex shrink-0 items-center gap-3 sm:w-40">
                  <Switch checked={d.on} onChange={(on) => setDay(i, { on })} label={WEEKDAYS_FULL[i]} />
                  <span className={cn("text-sm font-medium", !d.on && "text-muted-foreground")}>{WEEKDAYS_FULL[i]}</span>
                </div>
                {d.on ? (
                  <div className="flex w-full min-w-0 items-center gap-2 sm:w-auto sm:flex-1">
                    <div className="min-w-0 flex-1"><TimePicker value={d.start} onChange={(v) => setDay(i, { start: v })} aria-label={`${WEEKDAYS_FULL[i]}: начало`} /></div>
                    <span className="text-muted-foreground">–</span>
                    <div className="min-w-0 flex-1"><TimePicker value={d.end} onChange={(v) => setDay(i, { end: v })} aria-label={`${WEEKDAYS_FULL[i]}: конец`} aria-invalid={d.start >= d.end} /></div>
                    <motion.button type="button" {...press} title="Такое же время на все рабочие дни" aria-label="Такое же время на все рабочие дни" onClick={() => toAll(i)}
                      className="flex size-9 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors duration-fast hover:bg-surface hover:text-foreground"><CopyCheck className="size-4" /></motion.button>
                  </div>
                ) : <span className="ml-auto text-sm text-subtle-foreground sm:ml-0 sm:flex-1">Выходной</span>}
              </motion.li>
            ))}
          </motion.ul>
        )}
      </AnimatePresence>
    </div>
  );
};
