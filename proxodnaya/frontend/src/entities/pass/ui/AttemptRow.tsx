import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { REASONS, type Attempt } from "@/shared/api";
import { hhmm, dateRu, cn } from "@/shared/lib";
import { DecisionBadge } from "./DecisionBadge";

/** Строка журнала: направление, кто/причина, время и решение. who — слот для сотрудника (рисует слой выше). */
/** dirSource — подпись «определено автоматически / режим КПП» для успешных проходов. */
export const AttemptRow = ({ a, who, showDate, className, dirSource }: { a: Attempt; who?: React.ReactNode; showDate?: boolean; className?: string; dirSource?: string }) => (
  <div className={cn("flex min-w-0 items-center gap-3 py-3", className)}>
    <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-full", a.direction === "IN" ? "bg-accent text-accent-foreground" : "bg-surface text-muted-foreground")} title={a.direction === "IN" ? "Вход" : "Выход"}>
      {a.direction === "IN" ? <ArrowDownLeft className="size-4" /> : <ArrowUpRight className="size-4" />}
    </span>
    <div className="min-w-0 flex-1">
      {who}
      <div className={cn("truncate text-sm", who ? "text-muted-foreground" : "font-medium")}>{a.decision === "ALLOW" || a.code === "OK" ? (a.direction === "IN" ? "Вход" : "Выход") + (dirSource ? ` · ${dirSource}` : "") : REASONS[a.code].message}{a.note ? ` · ${a.note}` : ""}</div>
    </div>
    <div className="flex shrink-0 flex-col items-end gap-1">
      <span className="text-xs tabular-nums text-muted-foreground">{showDate ? `${dateRu(a.ts)}, ` : ""}{hhmm(a.ts)}</span>
      <DecisionBadge decision={a.decision} code={a.code} />
    </div>
  </div>
);
