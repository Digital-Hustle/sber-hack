import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useInfiniteQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { AlertTriangle, Inbox, Loader2, Search, UserRound } from "lucide-react";
import { AppStatusBadge, reviewApi, reviewKeys, waitingRu, type QueueFilter } from "@/entities/application";
import { useSession } from "@/entities/session";
import { TakeNextButton, useTakeNext } from "@/features/take-next";
import { Button, Card, EmptyState, Input, LoadMore, PageHeader, RowsSkeleton, Segmented, Select, Status } from "@/shared/ui";
import { errorText, useEventStream } from "@/shared/api";
import { routes } from "@/shared/const/router";
import { fadeUp, stagger } from "@/shared/config/motion";

const STATUSES = [
  { value: "", label: "Все активные" },
  { value: "SUBMITTED", label: "Ждут проверки" },
  { value: "IN_REVIEW", label: "На проверке" },
  { value: "NEEDS_CHANGES", label: "Ждём документы" },
  { value: "DRAFT", label: "Собираются" },
  { value: "ACCEPTED", label: "Принятые" },
  { value: "RETURNED", label: "Возвращённые" },
];

/** FE-OP-02: очередь — фильтры в URL, курсоры, «взять следующую», обновление по SSE без перезагрузки. */
export const QueuePage = () => {
  const [sp, setSp] = useSearchParams();
  const { login } = useSession();
  const qc = useQueryClient();
  const next = useTakeNext();
  const f: QueueFilter = { status: sp.get("status") ?? "", q: sp.get("q") ?? "", assignee: sp.get("assignee") ?? "", sort: sp.get("sort") ?? "waiting" };
  const [text, setText] = useState(f.q ?? "");
  const set = (k: string, v: string) => setSp((p) => { const n = new URLSearchParams(p); if (v) n.set(k, v); else n.delete(k); return n; }, { replace: true });
  useEffect(() => { const t = setTimeout(() => { if (text !== (f.q ?? "")) set("q", text.trim()); }, 300); return () => clearTimeout(t); });
  const q = useInfiniteQuery({
    queryKey: reviewKeys.queue(f),
    queryFn: ({ pageParam }) => reviewApi.queue(f, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.page?.next ?? null,
  });
  useEventStream("review", "/stream/ticket", "/stream", ["queue.changed", "document.stage"], () => { void qc.invalidateQueries({ queryKey: reviewKeys.queueAll }); });
  useEffect(() => {
    const on = (e: KeyboardEvent) => { if (e.key === "n" && !(e.target instanceof HTMLInputElement) && !next.isPending) next.mutate(); };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  }, [next]);
  const items = q.data?.pages.flatMap((p) => p.items ?? []) ?? [];
  const total = q.data?.pages[0]?.page?.total ?? 0;

  return (
    <>
      <PageHeader title="Очередь заявок" sub="Сначала — дольше всех ждущие. Поля, в которых модель не уверена, отмечены" actions={<TakeNextButton />} />
      <div className="mb-4 flex flex-col gap-2 lg:flex-row lg:items-center">
        <Input icon={<Search />} value={text} onChange={(e) => setText(e.target.value)} placeholder="ФИО соискателя или номер заявки" className="lg:max-w-sm" aria-label="Поиск" />
        <Select aria-label="Статус" className="lg:w-56" value={f.status ?? ""} options={STATUSES} onChange={(v) => set("status", v)} />
        <Segmented label="Чьи" value={f.assignee ? "me" : "all"} onChange={(v) => set("assignee", v === "me" ? login : "")} options={[{ value: "all", label: "Все" }, { value: "me", label: "Мои" }]} />
        <Segmented label="Сортировка" className="lg:ml-auto" value={f.sort === "updated" ? "updated" : "waiting"} onChange={(v) => set("sort", v === "waiting" ? "" : v)}
          options={[{ value: "waiting", label: "Дольше ждут" }, { value: "updated", label: "Недавние" }]} />
      </div>
      {q.isPending ? <RowsSkeleton rows={8} /> : q.isError ? (
        <EmptyState title="Очередь не загрузилась" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
      ) : !items.length ? (
        <Card><EmptyState icon={<Inbox />} title="Заявок нет" text={f.status || f.q || f.assignee ? "Под фильтр ничего не попало" : "Как только соискатель отправит комплект, он появится здесь"} /></Card>
      ) : (
        <>
          <motion.ul variants={stagger(0.02)} initial="hidden" animate="show" className="flex flex-col gap-2">
            {items.map((it) => (
              <motion.li key={it.id} variants={fadeUp}>
                <Link to={routes.operatorApplication(it.id!)} className="block rounded-lg bg-card p-4 shadow-card outline-none transition-shadow duration-fast hover:shadow-float focus-visible:ring-2 focus-visible:ring-ring">
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                    <div className="min-w-0 flex-1 basis-60">
                      <div className="truncate font-semibold">{it.applicantName || "Соискатель (имя ещё не распознано)"}</div>
                      <div className="text-xs text-muted-foreground">№ {it.id?.slice(0, 8)} · ждёт {waitingRu(it.waitingSeconds)}</div>
                    </div>
                    <AppStatusBadge status={it.status} />
                    <span className="text-sm tabular-nums text-muted-foreground">{it.confirmed ?? 0}/{it.documents ?? 0} док.</span>
                    {!!it.attention && <Status tone="warning"><AlertTriangle />перепроверить: {it.attention}</Status>}
                    {!!it.processing && <Status tone="info"><Loader2 />разбирается: {it.processing}</Status>}
                    {!!it.missing?.length && <Status tone="danger">не хватает: {it.missing.length}</Status>}
                    {it.assignee && <Status><UserRound />{it.assignee}</Status>}
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
