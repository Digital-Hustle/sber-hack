import { useState } from "react";
import { Check } from "lucide-react";
import { api, useDb } from "@/shared/api";
import { Avatar, Button, Dialog, Field, DateRangePicker, toast, type DateRange } from "@/shared/ui";
import { WeekPlanEditor, weekPlan, planDays, planValid, type WeekPlan } from "@/features/plan-week";
import { cn, todayKey, plural } from "@/shared/lib";

const addDays = (n: number) => { const d = new Date(); d.setDate(d.getDate() + n); return todayKey(d); };

/** Назначение графика: несколько сотрудников × дни недели × период. Шаблоны заполняют дни и время. */
export const ScheduleDialog = ({ open, onClose }: { open: boolean; onClose: () => void }) => {
  const db = useDb();
  const [who, setWho] = useState<string[]>([]);
  const [plan, setPlan] = useState<WeekPlan>(() => weekPlan([0, 1, 2, 3, 4], "08:00", "17:00"));
  const [range, setRange] = useState<DateRange>({ from: todayKey(), to: addDays(13) });
  const [busy, setBusy] = useState(false);
  const toggle = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);
  const contractors = [...new Set(db.workers.map((w) => w.contractor))];
  const ok = who.length > 0 && planValid(plan);
  const save = async () => {
    setBusy(true);
    const n = await api.assignSchedule({ workerIds: who, weekdays: [], start: "", end: "", days: planDays(plan), from: range.from, to: range.to });
    setBusy(false); onClose();
    toast.success(`Назначено ${n} ${plural(n, "смена", "смены", "смен")}`);
  };
  return (
    <Dialog open={open} onClose={onClose} title="Назначить график" description="Смены создаются на каждый выбранный день периода и заменяют уже назначенные"
      footer={<Button disabled={!ok || busy} onClick={save}>Назначить</Button>}>
      <div className="flex flex-col gap-5">
        <WeekPlanEditor value={plan} onChange={setPlan} />
        <Field label="Период"><DateRangePicker value={range} onChange={setRange} presets={[
          { label: "Эта неделя", range: { from: todayKey(), to: addDays(6 - ((new Date().getDay() + 6) % 7)) } },
          { label: "2 недели", range: { from: todayKey(), to: addDays(13) } },
          { label: "Месяц", range: { from: todayKey(), to: addDays(29) } },
        ]} /></Field>
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-medium">Сотрудники</span><span className="text-muted-foreground">· выбрано {who.length}</span>
            <span className="flex-1" />
            {contractors.map((c) => <button key={c} type="button" onClick={() => setWho([...new Set([...who, ...db.workers.filter((w) => w.contractor === c).map((w) => w.id)])])} className="rounded-sm px-1.5 text-xs font-medium text-accent-foreground hover:underline">+ {c}</button>)}
            <button type="button" onClick={() => setWho(who.length === db.workers.length ? [] : db.workers.map((w) => w.id))} className="rounded-sm px-1.5 text-xs font-medium text-accent-foreground hover:underline">{who.length === db.workers.length ? "Снять всех" : "Все"}</button>
          </div>
          <div className="flex max-h-56 flex-col overflow-y-auto rounded-md border border-border">
            {db.workers.map((w) => {
              const on = who.includes(w.id);
              return (
                <button key={w.id} type="button" role="checkbox" aria-checked={on} onClick={() => setWho(toggle(who, w.id))} className="flex min-w-0 items-center gap-3 border-b border-border px-3 py-2 text-left last:border-0 hover:bg-surface">
                  <span className={cn("flex size-5 shrink-0 items-center justify-center rounded-xs border transition-colors duration-fast", on ? "border-primary bg-primary text-primary-foreground" : "border-border-strong")}>{on && <Check className="size-3.5" />}</span>
                  <Avatar name={w.fullName} photo={w.photo} className="size-7 text-xs" />
                  <span className="min-w-0 flex-1"><span className="block truncate text-sm">{w.fullName}</span><span className="block truncate text-xs text-muted-foreground">{w.position} · {w.contractor}</span></span>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </Dialog>
  );
};
