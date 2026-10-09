import { useEffect, useState } from "react";
import { Link } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { AlertTriangle, Smartphone } from "lucide-react";
import { api, type FaceCheck } from "@/shared/api";
import { DrawnCheck, Spinner } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { fadeUp } from "@/shared/config/motion";
import { PhotoCapture } from "./PhotoCapture";

export type FaceCheckState = { status: "idle" } | { status: "checking" } | { status: "done"; result: FaceCheck };

/**
 * ADR-047: эталон лица при оформлении. Снимок сразу уходит на проверку (качество + не заведено ли лицо на
 * другого человека); ошибка видна, пока человек ещё перед камерой, — HR переснимает за секунды.
 */
export const FaceReferenceCapture = ({ value, onChange, workerId, onCheck, allowSkip = true }: {
  value?: string; onChange: (v?: string) => void; workerId?: string; onCheck: (s: FaceCheckState) => void; allowSkip?: boolean;
}) => {
  const [state, setState] = useState<FaceCheckState>({ status: "idle" });
  useEffect(() => {
    if (!value) { setState({ status: "idle" }); onCheck({ status: "idle" }); return; }
    let alive = true;
    setState({ status: "checking" }); onCheck({ status: "checking" });
    api.checkFacePhoto(value, workerId).then((result) => { if (!alive) return; const s = { status: "done" as const, result }; setState(s); onCheck(s); });
    return () => { alive = false; };
  }, [value, workerId]); // eslint-disable-line react-hooks/exhaustive-deps

  const r = state.status === "done" ? state.result : null;
  return (
    <div className="flex flex-col items-center gap-4">
      <PhotoCapture value={value} onChange={onChange} />
      <div className="w-full max-w-md" aria-live="polite">
        <AnimatePresence mode="wait" initial={false}>
          {state.status === "checking" && (
            <motion.div key="busy" variants={fadeUp} initial="hidden" animate="show" exit="exit" className="flex items-center justify-center gap-2.5 rounded-md bg-muted px-4 py-3 text-sm text-muted-foreground">
              <Spinner className="size-4" />Проверяем снимок: свет, резкость, нет ли этого лица у другого сотрудника…
            </motion.div>
          )}
          {r?.ok && (
            <motion.div key="ok" variants={fadeUp} initial="hidden" animate="show" exit="exit" className="flex items-start gap-3 rounded-md border border-success-border bg-success-soft px-4 py-3 text-sm text-success-soft-foreground">
              <DrawnCheck className="mt-px size-5 shrink-0 bg-success text-white" />
              <span><span className="font-medium">{r.message}</span> · качество {Math.round(r.score * 100)}%<span className="block opacity-80">{r.hint}</span></span>
            </motion.div>
          )}
          {r && !r.ok && (
            <motion.div key={r.code} variants={fadeUp} initial="hidden" animate="show" exit="exit" className="flex items-start gap-3 rounded-md border border-danger-border bg-danger-soft px-4 py-3 text-sm text-danger-soft-foreground">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" />
              <span>
                <span className="font-medium">{r.message}</span><span className="block opacity-80">{r.hint}</span>
                {r.duplicate && <Link to={routes.adminPerson(r.duplicate.workerId)} target="_blank" className="mt-1 inline-block font-medium underline underline-offset-4">Карточка: {r.duplicate.fullName}</Link>}
              </span>
            </motion.div>
          )}
          {state.status === "idle" && allowSkip && (
            <motion.div key="skip" variants={fadeUp} initial="hidden" animate="show" exit="exit" className="flex items-start gap-3 rounded-md bg-muted px-4 py-3 text-sm text-muted-foreground">
              <Smartphone className="mt-0.5 size-4 shrink-0" />
              <span>Снимок при оформлении — лучший эталон: человек рядом, документ под рукой. Нет возможности снять сейчас — нажмите «Без фото»: сотрудник снимет лицо в приложении, служба безопасности сверит его с документом. До этого на проходной пропустит охранник.</span>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};
