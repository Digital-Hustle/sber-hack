import { CheckCircle2, XCircle, ShieldAlert, WifiOff, Info, type LucideIcon } from "lucide-react";
import type { Checkpoint, Decision, DecisionResult } from "@/shared/api";

/** Как показывать решение: цвет и иконка только по типу решения, текст — как пришёл с сервера. */
export const decisionView: Record<Decision, { label: string; icon: LucideIcon; bg: string; fg: string; tone: "success" | "danger" | "warning" | "neutral" }> = {
  ALLOW: { label: "Проход", icon: CheckCircle2, bg: "bg-decision-allow", fg: "text-decision-allow-foreground", tone: "success" },
  DENY: { label: "Отказ", icon: XCircle, bg: "bg-decision-deny", fg: "text-decision-deny-foreground", tone: "danger" },
  MANUAL: { label: "Вручную", icon: ShieldAlert, bg: "bg-decision-manual", fg: "text-decision-manual-foreground", tone: "warning" },
  ERROR: { label: "Ошибка", icon: WifiOff, bg: "bg-decision-error", fg: "text-decision-error-foreground", tone: "neutral" },
};

/** Повторный скан сразу после успешного прохода — не нарушение, а подсказка: нейтральный тон и иконка «информация». */
export const repeatScanView = { label: "Повтор", icon: Info, bg: "bg-decision-error", fg: "text-decision-error-foreground", tone: "neutral" as const };

export const viewForResult = (r: Pick<DecisionResult, "decision" | "code">) => (r.code === "REPEAT_SCAN" ? repeatScanView : decisionView[r.decision]);

/** Откуда взялось направление (ADR-037): КПП в режиме AUTO выводит его из присутствия, иначе — фиксированный режим турникета. */
export const directionSource = (cp?: Pick<Checkpoint, "mode">) => (!cp?.mode || cp.mode === "AUTO" ? "определено автоматически" : "режим КПП");
