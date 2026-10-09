import { Status } from "@/shared/ui";
import { statusView } from "../model/views";

export const AppStatusBadge = ({ status, className }: { status?: string | null; className?: string }) => {
  const v = statusView(status);
  return <Status tone={v.tone} className={className}>{v.label}</Status>;
};
