import { useState } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { CalendarClock } from "lucide-react";
import { AppStatusBadge, dateRuFull, reviewApi, reviewKeys } from "@/entities/application";
import { ExportExpiringButton } from "@/features/export-documents";
import { Button, Card, EmptyState, PageHeader, RowsSkeleton, Segmented, Status } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { routes } from "@/shared/const/router";

/** FE-OP-09 / B-04: документы, срок которых истекает в ближайшие N дней, + выгрузка XLSX. */
export const ExpiringPage = () => {
  const [days, setDays] = useState("30");
  const q = useQuery({ queryKey: reviewKeys.expiring(Number(days)), queryFn: () => reviewApi.expiring(Number(days)) });
  const items = q.data?.items ?? [];
  return (
    <>
      <PageHeader title="Истекающие документы" sub="Патенты, регистрации, медкнижки — чтобы продлить до того, как сотрудник выпадет из графика"
        actions={<><Segmented label="Период" value={days} onChange={setDays} options={["14", "30", "60", "90"].map((d) => ({ value: d, label: `${d} дн` }))} /><ExportExpiringButton days={Number(days)} /></>} />
      {q.isPending ? <RowsSkeleton rows={6} /> : q.isError ? (
        <EmptyState title="Отчёт не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<CalendarClock />} title="Ничего не истекает" text={`В ближайшие ${days} дней сроки документов не заканчиваются`} /></Card>
      ) : (
        <Card className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-muted-foreground"><tr className="border-b border-border">
              <th className="p-3 font-medium">Соискатель</th><th className="p-3 font-medium">Документ</th><th className="p-3 font-medium">Истекает</th><th className="p-3 font-medium">Осталось</th><th className="p-3 font-medium">Заявка</th>
            </tr></thead>
            <tbody>
              {items.map((it) => (
                <tr key={it.documentId} className="border-b border-border last:border-0 hover:bg-muted">
                  <td className="p-3 font-medium"><Link to={routes.operatorApplication(it.applicationId!)} className="hover:underline">{it.applicantName || "—"}</Link></td>
                  <td className="p-3">{it.typeName ?? it.type}</td>
                  <td className="p-3 tabular-nums">{dateRuFull(it.expiresOn)}</td>
                  <td className="p-3"><Status tone={(it.daysLeft ?? 0) < 0 ? "danger" : (it.daysLeft ?? 0) <= 14 ? "warning" : "neutral"}>{(it.daysLeft ?? 0) < 0 ? "истёк" : `${it.daysLeft} дн`}</Status></td>
                  <td className="p-3"><AppStatusBadge status={it.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </>
  );
};
