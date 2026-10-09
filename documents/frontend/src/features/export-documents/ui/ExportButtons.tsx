import { useState } from "react";
import { FileDown } from "lucide-react";
import { reviewApi } from "@/entities/application";
import { Button, toast } from "@/shared/ui";
import { errorText } from "@/shared/api";

const run = async (f: () => Promise<void>, set: (b: boolean) => void) => {
  set(true);
  try { await f(); } catch (e) { toast.error(errorText(e)); } finally { set(false); }
};

/** FE-OP-09 / B-05: карточка соискателя для кадров (DOCX/XLSX/PDF) — выгрузку пишет в историю сервер. */
export const ExportCardButtons = ({ appId }: { appId: string }) => {
  const [busy, setBusy] = useState(false);
  return (
    <div className="flex gap-1.5">
      <Button size="sm" variant="secondary" disabled={busy} onClick={() => run(() => reviewApi.exportCard(appId, "docx"), setBusy)}><FileDown />DOCX</Button>
      <Button size="sm" variant="secondary" disabled={busy} onClick={() => run(() => reviewApi.exportCard(appId, "xlsx"), setBusy)}><FileDown />XLSX</Button>
      <Button size="sm" variant="secondary" disabled={busy} onClick={() => run(() => reviewApi.exportCard(appId, "pdf"), setBusy)}><FileDown />PDF</Button>
    </div>
  );
};

/** FE-OP-09 / B-04: истекающие документы в XLSX. */
export const ExportExpiringButton = ({ days }: { days: number }) => {
  const [busy, setBusy] = useState(false);
  return <Button variant="secondary" disabled={busy} onClick={() => run(() => reviewApi.exportExpiring(days), setBusy)}><FileDown />{busy ? "Готовим…" : "Выгрузить XLSX"}</Button>;
};
