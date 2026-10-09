import { Link, useParams } from "react-router";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { ArrowLeft, History } from "lucide-react";
import { packageApi, packageKeys, PackageStatusBadge } from "@/entities/package";
import { HISTORY_ACTION, protocolApi, protocolKeys } from "@/entities/protocol";
import { errorText } from "@/shared/api";
import { Button, Card, CardContent, CardHeader, CardTitle, EmptyState, PageHeader, RowsSkeleton } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { agoRu } from "@/shared/lib";

const detailsText = (d: Record<string, unknown> | null) =>
  d ? Object.entries(d).filter(([, v]) => v !== null && v !== "").map(([k, v]) => `${k}: ${typeof v === "object" ? JSON.stringify(v) : String(v)}`).join(" · ") : "";

/** FE-SP-07: организация — её пакеты и лента событий (создание, протоколы, правки, решения). */
export const OrganizationPage = () => {
  const { inn = "" } = useParams();
  const history = useQuery({ queryKey: protocolKeys.history(inn), queryFn: () => protocolApi.history(inn) });
  const f = { inn };
  const packages = useInfiniteQuery({
    queryKey: packageKeys.queue(f), queryFn: ({ pageParam }) => packageApi.queue(f, pageParam),
    initialPageParam: null as string | null, getNextPageParam: (last) => last.page?.next ?? null,
  });
  const pk = packages.data?.pages.flatMap((p) => p.items ?? []) ?? [];
  const name = pk.find((p) => p.org.name)?.org.name;

  return (
    <>
      <Link to={routes.packages} className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="size-4" />Пакеты</Link>
      <PageHeader title={name ?? `ИНН ${inn}`} sub={`ИНН ${inn}`} />
      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader><CardTitle>Пакеты</CardTitle></CardHeader>
          <CardContent>
            {packages.isPending ? <RowsSkeleton rows={3} /> : !pk.length ? <p className="text-sm text-muted-foreground">Пакетов нет</p> : (
              <ul className="flex flex-col gap-2">
                {pk.map((p) => (
                  <li key={p.id}><Link to={routes.pkg(p.id)} className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-surface">
                    <span className="flex-1">{p.program}</span><PackageStatusBadge status={p.status} />
                  </Link></li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader><CardTitle>История</CardTitle></CardHeader>
          <CardContent>
            {history.isPending ? <RowsSkeleton rows={5} /> : history.isError ? <EmptyState title="История не загрузилась" text={errorText(history.error)} action={<Button variant="secondary" onClick={() => history.refetch()}>Повторить</Button>} />
              : !history.data.items.length ? <EmptyState icon={<History />} title="Событий нет" /> : (
                <ol className="flex flex-col gap-3 border-l border-border pl-4">
                  {history.data.items.map((h, i) => (
                    <li key={i} className="flex flex-col gap-0.5">
                      <div className="text-sm font-medium">{HISTORY_ACTION[h.action] ?? h.action}</div>
                      <div className="text-xs text-muted-foreground">{new Date(h.at).toLocaleString("ru-RU")} · {agoRu(Date.parse(h.at))}{h.by ? ` · ${h.by}` : ""}
                        {h.packageId && <> · <Link className="underline-offset-2 hover:underline" to={routes.pkg(h.packageId)}>пакет</Link></>}</div>
                      {detailsText(h.details) && <div className="text-xs text-muted-foreground">{detailsText(h.details)}</div>}
                    </li>
                  ))}
                </ol>
              )}
          </CardContent>
        </Card>
      </div>
    </>
  );
};
