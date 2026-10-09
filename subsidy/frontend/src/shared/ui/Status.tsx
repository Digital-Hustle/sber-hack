import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/shared/lib";

const status = cva("inline-flex h-6 max-w-full shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 text-xs font-medium [&_svg]:size-3.5 [&_svg]:shrink-0", {
  variants: {
    tone: {
      neutral: "border-border bg-surface text-muted-foreground",
      success: "border-success-border bg-success-soft text-success-soft-foreground",
      warning: "border-warning-border bg-warning-soft text-warning-soft-foreground",
      danger: "border-danger-border bg-danger-soft text-danger-soft-foreground",
      info: "border-info-border bg-info-soft text-info-soft-foreground",
    },
  },
  defaultVariants: { tone: "neutral" },
});

const DOT = { neutral: "bg-subtle-foreground", success: "bg-success", warning: "bg-warning", danger: "bg-danger", info: "bg-info" } as const;

export type Tone = NonNullable<VariantProps<typeof status>["tone"]>;

/** Статус: мягкий тинт + точка. Не сплошная заливка. */
export const Status = ({ tone = "neutral", dot, className, children, ...p }: React.HTMLAttributes<HTMLSpanElement> & VariantProps<typeof status> & { dot?: boolean }) => (
  <span className={cn(status({ tone }), className)} {...p}>
    {dot && <span className={cn("size-1.5 shrink-0 rounded-full", DOT[tone ?? "neutral"])} />}
    <span className="flex min-w-0 items-center gap-1.5 truncate">{children}</span>
  </span>
);
/** Совместимость: старое имя */
export const Badge = Status;
