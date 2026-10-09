import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { motion, AnimatePresence } from "motion/react";
import { Check, ArrowRight } from "lucide-react";
import { applicantApi, applicantKeys, type Question } from "@/entities/application";
import { Button, Segmented, Skeleton, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { cn } from "@/shared/lib";
import { fadeUp, press } from "@/shared/config/motion";

type Answers = Record<string, string | boolean>;

const visible = (q: Question, a: Answers) =>
  !q.showWhen || Object.entries(q.showWhen).every(([k, vals]) => vals.includes(String(a[k] ?? "")));

/** FE-AP-02: анкета → сервер считает нужный набор документов (FR-10). Вопросы и условия показа — с сервера. */
export const ProfileForm = ({ applicationId, initial, onDone }: { applicationId: string; initial?: Record<string, unknown>; onDone?: () => void }) => {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: applicantKeys.questionnaire, queryFn: applicantApi.questionnaire, staleTime: Infinity });
  const [a, setA] = useState<Answers>(() => (initial ?? {}) as Answers);
  const questions = useMemo(() => (q.data?.questions ?? []).filter((x) => visible(x, a)), [q.data, a]);
  const complete = questions.every((x) => a[x.id ?? ""] !== undefined);
  const m = useMutation({
    mutationFn: () => {
      const ids = new Set(questions.map((x) => x.id));
      return applicantApi.profile(applicationId, Object.fromEntries(Object.entries(a).filter(([k]) => ids.has(k))));
    },
    onSuccess: () => { void qc.invalidateQueries({ queryKey: applicantKeys.application(applicationId) }); onDone?.(); },
    onError: (e) => toast.error(errorText(e)),
  });

  if (q.isPending) return <div className="flex flex-col gap-3"><Skeleton className="h-6 w-2/3" /><Skeleton className="h-control-lg" /><Skeleton className="h-control-lg" /></div>;
  if (q.isError) return <Button variant="secondary" onClick={() => q.refetch()}>Не удалось загрузить анкету — повторить</Button>;

  return (
    <form className="flex flex-col gap-6" onSubmit={(e) => { e.preventDefault(); if (complete && !m.isPending) m.mutate(); }}>
      <AnimatePresence initial={false}>
        {questions.map((x) => (
          <motion.fieldset key={x.id} variants={fadeUp} initial="hidden" animate="show" exit="hidden" className="flex flex-col gap-3">
            <legend className="mb-3 text-base font-semibold">{x.text}</legend>
            {x.kind === "bool" ? (
              <Segmented block size="lg" label={x.text} value={a[x.id!] === undefined ? "" : a[x.id!] ? "yes" : "no"}
                onChange={(v) => setA((s) => ({ ...s, [x.id!]: v === "yes" }))} options={[{ value: "yes", label: "Да" }, { value: "no", label: "Нет" }]} />
            ) : (
              <div className="flex flex-col gap-2" role="radiogroup" aria-label={x.text}>
                {(x.options ?? []).map((o) => {
                  const on = a[x.id!] === o.value;
                  return (
                    <motion.button key={o.value} type="button" role="radio" aria-checked={on} {...press} onClick={() => setA((s) => ({ ...s, [x.id!]: o.value! }))}
                      className={cn("flex min-h-control-lg items-center gap-3 rounded-lg border px-4 py-3 text-left transition-colors duration-fast",
                        on ? "border-primary bg-accent text-accent-foreground" : "border-border bg-card hover:bg-muted")}>
                      <span className="flex min-w-0 flex-1 flex-col"><span className="font-medium">{o.label}</span>{o.hint && <span className="text-sm text-muted-foreground">{o.hint}</span>}</span>
                      <span className={cn("grid size-6 shrink-0 place-items-center rounded-full border", on ? "border-primary bg-primary text-primary-foreground" : "border-border-strong")}>{on && <Check className="size-4" />}</span>
                    </motion.button>
                  );
                })}
              </div>
            )}
          </motion.fieldset>
        ))}
      </AnimatePresence>
      <Button type="submit" size="lg" block disabled={!complete || m.isPending}>{m.isPending ? "Сохраняем…" : "Показать список документов"}<ArrowRight /></Button>
    </form>
  );
};
