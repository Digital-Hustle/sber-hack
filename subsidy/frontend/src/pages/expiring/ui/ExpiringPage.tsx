import { useState } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { CalendarClock } from "lucide-react";
import { protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, Card, EmptyState, PageHeader, RowsSkeleton, Segmented, Status } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { plural } from "@/shared/lib";

const RANGES = [{ value: "7", label: "7 дней" }, { value: "14", label: "14 дней" }, { value: "30", label: "30 дней" }];
const URGENT_DAYS = 3;

/** FE-SP-09: справки и выписки, у которых истекает срок действия — срок считает rules. */
export const ExpiringPage = () => {
  const [days, setDays] = useState("14");
  const q = useQuery({ queryKey: protocolKeys.expiring(Number(days)), queryFn: () => protocolApi.expiring(Number(days)) });
  const items = q.data?.items ?? [];
  return (
    <>
      <PageHeader title="Истекающие документы" sub="Справки и выписки, которые скоро перестанут действовать — запросите новые заранее"
        actions={<Segmented label="Горизонт" value={days} onChange={setDays} options={RANGES} />} />
      {q.isPending ? <RowsSkeleton rows={6} /> : q.isError ? (
        <EmptyState title="Список не загрузился" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<CalendarClock />} title="Ничего не истекает" text={`В ближайшие ${days} дней сроки не заканчиваются`} className="py-16" /></Card>
      ) : (
        <ul className="flex flex-col gap-2">
          {items.map((it) => (
            <li key={it.documentId}>
              <Link to={`${routes.pkg(it.packageId)}?tab=documents&doc=${it.documentId}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg bg-card p-4 shadow-card outline-none transition-shadow duration-fast hover:shadow-float focus-visible:ring-2 focus-visible:ring-ring">
                <div className="min-w-0 flex-1 basis-60">
                  <div className="truncate font-semibold">{it.title}</div>
                  <div className="text-xs text-muted-foreground">ИНН {it.inn} · от {new Date(it.date).toLocaleDateString("ru-RU")} · действует до {new Date(it.expiresAt).toLocaleDateString("ru-RU")}</div>
                </div>
                <Status tone={it.daysLeft <= URGENT_DAYS ? "danger" : "warning"}>
                  {it.daysLeft <= 0 ? "истёк" : `осталось ${it.daysLeft} ${plural(it.daysLeft, "день", "дня", "дней")}`}
                </Status>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
};
