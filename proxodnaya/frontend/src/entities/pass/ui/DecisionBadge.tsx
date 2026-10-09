import { Status } from "@/shared/ui";
import type { Decision, ReasonCode } from "@/shared/api";
import { viewForResult } from "../model/decisionView";

export const DecisionBadge = ({ decision, code }: { decision: Decision; code?: ReasonCode }) => {
  const v = viewForResult({ decision, code: code ?? "OK" });
  return <Status tone={v.tone}>{v.label}</Status>;
};
