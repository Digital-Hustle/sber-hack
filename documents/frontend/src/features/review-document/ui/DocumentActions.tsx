import { useQuery } from "@tanstack/react-query";
import { CheckCheck, RotateCcw } from "lucide-react";
import { reviewApi, reviewKeys, type DocumentCard } from "@/entities/application";
import { Button, Select } from "@/shared/ui";
import { useConfirmDocument, useReprocessDocument, useRetypeDocument } from "../model/mutations";

/** FE-OP-05/07: подтвердить, сменить вид (переизвлечение по новой схеме), разобрать заново (старший оператор). */
export const DocumentActions = ({ appId, doc, canAct, canReprocess }: { appId: string; doc: DocumentCard; canAct: boolean; canReprocess: boolean }) => {
  const types = useQuery({ queryKey: reviewKeys.types, queryFn: reviewApi.types, staleTime: Infinity, enabled: canAct });
  const confirm = useConfirmDocument(appId);
  const retype = useRetypeDocument(appId);
  const reprocess = useReprocessDocument(appId);
  const options = (types.data?.items ?? []).map((t) => ({ value: t.code!, label: t.name ?? t.code! }));
  return (
    <div className="flex flex-wrap items-center gap-2">
      {canAct && options.length > 0 && (
        <Select size="sm" aria-label="Вид документа" className="w-56" value={doc.type ?? ""} options={options}
          onChange={(t) => t !== doc.type && retype.mutate({ docId: doc.id!, type: t })} />
      )}
      {canReprocess && <Button size="sm" variant="secondary" disabled={reprocess.isPending} onClick={() => reprocess.mutate(doc.id!)}><RotateCcw />Разобрать заново</Button>}
      {canAct && (
        <Button size="sm" variant={doc.confirmed ? "accent" : "primary"} disabled={doc.confirmed || confirm.isPending} onClick={() => confirm.mutate(doc.id!)}>
          <CheckCheck />{doc.confirmed ? "Подтверждён" : "Подтвердить"}<kbd className="ml-1 rounded-xs bg-white/20 px-1.5 text-xs max-md:hidden">Enter</kbd>
        </Button>
      )}
    </div>
  );
};
