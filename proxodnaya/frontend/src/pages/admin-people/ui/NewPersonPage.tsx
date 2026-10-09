import { useState } from "react";
import { useNavigate, Link } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, ArrowRight } from "lucide-react";
import { api, useDb, type Worker } from "@/shared/api";
import { Button, Card, Chip, Field, Input, Select, toast, PageHeader } from "@/shared/ui";
import { WeekPlanEditor, weekPlan, planDays, planValid, type WeekPlan } from "@/features/plan-week";
import { cn, todayKey, hhmm } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { spring, tween } from "@/shared/config/motion";
import { FaceReferenceCapture, type FaceCheckState } from "@/features/capture-photo";
import { useSession } from "@/entities/session";
import { InviteCard } from "./InviteCard";

const STEPS = ["Данные", "Фото", "Смена", "Приглашение"];
const CONTRACTORS = ["Генподрядчик", "СтройМонтаж", "Бетон-Юг", "ЭлектроСеть", "ИнжСистемы", "ТехноКран"];

/** Заведение человека за ≤ 2 минуты (Д9): данные → фото → смена → инвайт. */
const PLAN_DAYS = 14;

export const NewPersonPage = () => {
  const db = useDb();
  const nav = useNavigate();
  const [step, setStep] = useState(0);
  const [dir, setDir] = useState(1);
  const [fullName, setFullName] = useState("");
  const [touched, setTouched] = useState(false);
  const [position, setPosition] = useState("Монтажник");
  const [contractor, setContractor] = useState(CONTRACTORS[1]);
  // По умолчанию — первые две зоны (в демо — корпуса А и Б); объекты администратор может пересоздать (ADR-047).
  const [zoneIds, setZoneIds] = useState<string[]>(() => db.zones.slice(0, 2).map((z) => z.id));
  const [photo, setPhoto] = useState<string>();
  const [check, setCheck] = useState<FaceCheckState>({ status: "idle" });
  const by = useSession((x) => x.userId);
  // По умолчанию — каждый день и время, в которое сотрудник может пройти уже сейчас (удобно для демо).
  const [plan, setPlan] = useState<WeekPlan>(() => { const h = new Date().getHours(); return weekPlan([0, 1, 2, 3, 4, 5, 6], hhmm(Date.now() - 30 * 60000).slice(0, 2) + ":00", `${String(Math.min(23, Math.max(h + 8, 17))).padStart(2, "0")}:00`); });
  const [created, setCreated] = useState<Worker | null>(null);
  const [busy, setBusy] = useState(false);
  const live = created ? db.workers.find((w) => w.id === created.id) ?? created : null;

  const nameOk = fullName.trim().split(/\s+/).length >= 2;
  const go = (d: number) => { setDir(d); setStep((s) => s + d); };
  const finish = async () => {
    setBusy(true);
    let w: Worker;
    try { w = await api.createWorker({ fullName: fullName.trim(), position, contractor, zoneIds, photo }, by); }
    catch (e) { setBusy(false); toast.error(e instanceof Error ? e.message : "Не удалось завести сотрудника"); setDir(-1); setStep(1); return; }
    const to = new Date(); to.setDate(to.getDate() + PLAN_DAYS - 1);
    await api.assignSchedule({ workerIds: [w.id], weekdays: [], start: "", end: "", days: planDays(plan), from: todayKey(), to: todayKey(to) });
    setCreated(w); setBusy(false); go(1);
    toast.success("Сотрудник заведён");
  };
  const photoOk = !photo || (check.status === "done" && check.result.ok);
  const canNext = [nameOk && zoneIds.length > 0, photoOk, planValid(plan)][step] ?? true;

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title="Новый сотрудник" sub="Профиль, фото, смена и приглашение для активации пропуска" />

      <ol className="mb-4 grid grid-cols-4 gap-2 sm:mb-5" aria-label="Шаги">
        {STEPS.map((s, i) => (
          <li key={s} className="flex min-w-0 flex-col gap-2" aria-current={i === step ? "step" : undefined}>
            <div className="h-1 overflow-hidden rounded-full bg-surface"><motion.div className="h-full origin-left rounded-full bg-primary" initial={false} animate={{ scaleX: i <= step ? 1 : 0 }} transition={spring.bar} /></div>
            <span className={cn("truncate text-xs font-medium", i === step ? "text-foreground" : "text-muted-foreground", i !== step && "hidden sm:block")}>{i + 1}. {s}</span>
          </li>
        ))}
      </ol>

      <Card className="overflow-hidden">
        <div className="p-4 sm:p-6">
          <AnimatePresence mode="wait" custom={dir} initial={false}>
            <motion.div key={step} custom={dir} initial={{ x: dir * 32, opacity: 0 }} animate={{ x: 0, opacity: 1 }} exit={{ x: dir * -32, opacity: 0, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.fast }}>
              {step === 0 && (
                <div className="flex flex-col gap-5">
                  <Field label="ФИО" error={touched && !nameOk ? "Нужны хотя бы фамилия и имя" : null}>
                    <Input autoFocus value={fullName} onChange={(e) => setFullName(e.target.value)} onBlur={() => setTouched(true)} placeholder="Фамилия Имя Отчество" autoComplete="off" />
                  </Field>
                  <div className="grid gap-5 sm:grid-cols-2">
                    <Field label="Должность"><Input value={position} onChange={(e) => setPosition(e.target.value)} /></Field>
                    <Field label="Подрядчик"><Select value={contractor} onChange={setContractor} options={CONTRACTORS.map((c) => ({ value: c, label: c }))} /></Field>
                  </div>
                  <fieldset className="flex flex-col gap-2">
                    <legend className="mb-1.5 text-sm font-medium">Допуск в зоны</legend>
                    <div className="flex flex-wrap gap-2">
                      {db.zones.map((z) => {
                        const on = zoneIds.includes(z.id);
                        return <Chip key={z.id} pressed={on} onClick={() => setZoneIds((v) => (on ? v.filter((x) => x !== z.id) : [...v, z.id]))}>{z.name}</Chip>;
                      })}
                    </div>
                    {zoneIds.length === 0 && <p className="text-xs text-danger">Выберите хотя бы одну зону</p>}
                  </fieldset>
                </div>
              )}
              {step === 1 && <FaceReferenceCapture value={photo} onChange={setPhoto} onCheck={setCheck} />}
              {step === 2 && (
                <div className="flex flex-col gap-5">
                  <p className="text-sm text-muted-foreground">График на {PLAN_DAYS} дней с сегодняшнего. Без смены киоск не допустит сотрудника; потом график меняется в «Сменах».</p>
                  <WeekPlanEditor value={plan} onChange={setPlan} />
                </div>
              )}
              {step === 3 && live && <InviteCard w={live} />}
            </motion.div>
          </AnimatePresence>
        </div>
        <div className="flex items-center gap-2 border-t border-border px-4 py-3 sm:px-6 sm:py-4">
          {step > 0 && step < 3 && <Button variant="quiet" onClick={() => go(-1)}><ArrowLeft />Назад</Button>}
          {step === 0 && <Link to={routes.adminPeople} tabIndex={-1}><Button variant="quiet">Отмена</Button></Link>}
          <div className="ml-auto flex min-w-0">
            {step < 2 && <Button disabled={!canNext} onClick={() => { setTouched(true); go(1); }}>{step === 1 && !photo ? "Без фото" : step === 1 && check.status === "checking" ? "Проверяем…" : "Дальше"}<ArrowRight /></Button>}
            {step === 2 && <Button disabled={!canNext || busy} onClick={finish}><span className="truncate">Создать и выдать QR</span><ArrowRight /></Button>}
            {step === 3 && live && <Button variant="secondary" onClick={() => nav(routes.adminPerson(live.id))}>Карточка сотрудника</Button>}
          </div>
        </div>
      </Card>
    </div>
  );
};
