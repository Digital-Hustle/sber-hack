import { Status } from "@/shared/ui";
import type { Worker } from "@/shared/api";
import { todayKey } from "@/shared/lib";

export const WorkerStatusBadge = ({ w, inside }: { w: Worker; inside?: boolean }) => {
  if (w.status === "blocked") return <Status tone="danger">Заблокирован</Status>;
  if (w.permitUntil < todayKey()) return <Status tone="warning">Допуск просрочен</Status>;
  if (inside) return <Status tone="success">На объекте</Status>;
  return <Status>Не на объекте</Status>;
};
