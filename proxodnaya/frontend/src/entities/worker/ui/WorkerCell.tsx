import { Avatar } from "@/shared/ui";
import type { Worker } from "@/shared/api";
import { cn } from "@/shared/lib";

export const WorkerCell = ({ w, sub, className }: { w: Pick<Worker, "fullName" | "photo" | "position">; sub?: React.ReactNode; className?: string }) => (
  <div className={cn("flex min-w-0 items-center gap-3", className)}>
    <Avatar name={w.fullName} photo={w.photo} />
    <div className="min-w-0">
      <div className="truncate text-sm font-medium">{w.fullName}</div>
      <div className="truncate text-sm text-muted-foreground">{sub ?? w.position}</div>
    </div>
  </div>
);
