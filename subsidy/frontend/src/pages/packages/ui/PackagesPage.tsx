import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useInfiniteQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { AlertTriangle, FolderOpen, Search, XCircle } from "lucide-react";
import { packageApi, packageKeys, PackageStatusBadge, ProtocolStatusBadge, type QueueFilter } from "@/entities/package";
import { can, useSession } from "@/entities/session";
import { CreatePackageButton } from "@/features/create-package";
import { Button, Card, EmptyState, Input, LoadMore, PageHeader, RowsSkeleton, Select, Status } from "@/shared/ui";
import { errorText } from "@/shared/api";
import { routes } from "@/shared/const/router";
import { fadeUp, stagger } from "@/shared/config/motion";
import { agoRu } from "@/shared/lib";

const POLL_MS = 10_000;
const STATUSES = [
  { value: "", label: "Все пакеты" },
  { value: "COLLECTING", label: "Собираются" },
  { value: "PROCESSING", label: "Разбираются" },
  { value: "CHECKED", label: "Проверены" },
  { value: "DECIDED", label: "С решением" },
];

/** FE-SP-02: очередь пакетов — фильтры в URL, курсоры, сомнительные видны сразу; обновление раз в 10 с. */
export const PackagesPage = () => {
  const [sp, setSp] = useSearchParams();
  const { roles } = useSession();
  const f: QueueFilter = { status: sp.get("status") ?? "", inn: sp.get("inn") ?? "" };
  const [text, setText] = useState(f.inn ?? "");
  const set = (k: string, v: string) => setSp((p) => { const n = new URLSearchParams(p); if (v) n.set(k, v); else n.delete(k); return n; }, { replace: true });
  useEffect(() => { const t = setTimeout(() => { if (text !== (f.inn ?? "")) set("inn", text.trim()); }, 300); return () => clearTimeout(t); });
  const q = useInfiniteQuery({
    queryKey: packageKeys.queue(f),
    queryFn: ({ pageParam }) => packageApi.queue(f, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.page?.next ?? null,
    refetchInterval: POLL_MS,
  });
  const items = q.data?.pages.flatMap((p) => p.items ?? []) ?? [];
  const total = q.data?.pages[0]?.page?.total ?? 0;

  return (
    <>
      <PageHeader title="Пакеты" sub="Пакеты документов организаций на субсидию: что не хватает и где ошибки — сразу в списке" actions={can(roles, "work") ? <CreatePackageButton /> : undefined} />
      <div className="mb-4 flex flex-col gap-2 lg:flex-row lg:items-center">
        <Input icon={<Search />} value={text} inputMode="numeric" onChange={(e) => setText(e.target.value.replace(/\D/g, ""))} placeholder="ИНН организации" className="lg:max-w-xs" aria-label="ИНН" />
        <Select aria-label="Статус" className="lg:w-56" value={f.status ?? ""} options={STATUSES} onChange={(v) => set("status", v)} />
      </div>
      {q.isPending ? <RowsSkeleton rows={8} /> : q.isError ? (
        <EmptyState title="Пакеты не загрузились" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<FolderOpen />} title="Пакетов нет" text={f.status || f.inn ? "Под фильтр ничего не попало" : "Создайте пакет и загрузите документы организации"} className="py-16" /></Card>
      ) : (
        <>
          <motion.ul variants={stagger(0.02)} initial="hidden" animate="show" className="flex flex-col gap-2">
            {items.map((it) => (
              <motion.li key={it.id} variants={fadeUp}>
                <Link to={routes.pkg(it.id)} className="block rounded-lg bg-card p-4 shadow-card outline-none transition-shadow duration-fast hover:shadow-float focus-visible:ring-2 focus-visible:ring-ring">
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                    <div className="min-w-0 flex-1 basis-60">
                      <div className="truncate font-semibold">{it.org.name || `ИНН ${it.org.inn}`}</div>
                      <div className="text-xs text-muted-foreground">ИНН {it.org.inn} · {it.program} · обновлён {agoRu(Date.parse(it.updatedAt))}</div>
                    </div>
                    <PackageStatusBadge status={it.status} />
                    {it.status !== "COLLECTING" && it.status !== "PROCESSING" && <ProtocolStatusBadge status={it.protocolStatus} />}
                    {it.completeness !== null && <span className="text-sm tabular-nums text-muted-foreground">комплект {it.completeness}%</span>}
                    {!!it.failed && <Status tone="danger"><XCircle />ошибок: {it.failed}</Status>}
                    {!!it.warnings && <Status tone="warning"><AlertTriangle />предупреждений: {it.warnings}</Status>}
                    {!!it.needsReview && <Status tone="info">проверить вручную: {it.needsReview}</Status>}
                  </div>
                </Link>
              </motion.li>
            ))}
          </motion.ul>
          <LoadMore className="mt-4" shown={items.length} total={total} hasMore={!!q.hasNextPage} loading={q.isFetchingNextPage} error={q.isFetchNextPageError} onMore={() => q.fetchNextPage()} />
        </>
      )}
    </>
  );
};
