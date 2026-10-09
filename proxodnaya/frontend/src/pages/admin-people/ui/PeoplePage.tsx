import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { motion } from "motion/react";
import { Search, UserPlus, Users, ChevronRight } from "lucide-react";
import { api, useDb, presenceNow, type WorkerFilter } from "@/shared/api";
import { Button, Card, EmptyState, Input, Segmented, Select, PageHeader, LoadMore, RowsSkeleton } from "@/shared/ui";
import { WorkerCell, WorkerStatusBadge } from "@/entities/worker";
import { usePaged, useDebounced } from "@/shared/hooks";
import { routes } from "@/shared/const/router";
import { tween } from "@/shared/config/motion";

export const PeoplePage = () => {
  const db = useDb();
  const [sp, setSp] = useSearchParams();
  const set = (k: string, v: string) => setSp((p) => { if (v) p.set(k, v); else p.delete(k); return p; }, { replace: true });
  const [q, setQ] = useState(sp.get("q") ?? "");
  const dq = useDebounced(q);
  const f = (sp.get("f") as WorkerFilter) ?? "all";
  const zone = sp.get("zone") ?? "";
  const firm = sp.get("firm") ?? "";
  const insideCount = useMemo(() => presenceNow(db).length, [db]);
  const firms = useMemo(() => [...new Set(db.workers.map((w) => w.contractor))].sort((a, b) => a.localeCompare(b, "ru")), [db.workers]);
  const page = usePaged((cursor, limit) => api.queryWorkers({ q: dq, filter: f, zoneId: zone || undefined, contractor: firm || undefined, cursor, limit }),
    JSON.stringify([dq, f, zone, firm]), { live: db, id: (w) => w.id });
  const filtered = !!(dq || f !== "all" || zone || firm);
  const reset = () => { setQ(""); setSp(new URLSearchParams(), { replace: true }); };

  return (
    <div>
      <PageHeader title="Люди" sub={`${db.workers.length} в базе · ${insideCount} на объекте`} actions={<Link to={routes.adminPersonNew} tabIndex={-1}><Button><UserPlus />Новый сотрудник</Button></Link>} />
      <div className="mb-3 grid gap-2 sm:mb-4 sm:grid-cols-2 lg:grid-cols-6">
        <div className="min-w-0 sm:col-span-2 lg:col-span-2"><Input icon={<Search />} value={q} onChange={(e) => { setQ(e.target.value); set("q", e.target.value); }} placeholder="ФИО, должность, подрядчик" aria-label="Поиск" className="bg-card" /></div>
        <Segmented value={f} onChange={(v) => set("f", v === "all" ? "" : v)} label="Фильтр" className="self-start sm:col-span-2 lg:col-span-2"
          options={[{ value: "all", label: "Все" }, { value: "inside", label: "На объекте" }, { value: "blocked", label: "Заблокированы" }]} />
        <Select aria-label="Зона допуска" value={zone} onChange={(v) => set("zone", v)} className="bg-card" options={[{ value: "", label: "Все зоны" }, ...db.zones.map((z) => ({ value: z.id, label: z.name }))]} />
        <Select aria-label="Подрядчик" value={firm} onChange={(v) => set("firm", v)} className="bg-card" options={[{ value: "", label: "Все подрядчики" }, ...firms.map((c) => ({ value: c, label: c }))]} />
      </div>
      <Card className="overflow-hidden">
        {!page.ready ? <RowsSkeleton /> : page.items.length === 0 && !page.error ? (
          <EmptyState icon={<Users />} title={filtered ? "Никого не нашли" : "В базе пока никого"} text={filtered ? "Измените запрос или фильтры" : "Заведите первого сотрудника — это займёт пару минут"}
            action={filtered ? <Button variant="secondary" onClick={reset}>Сбросить фильтры</Button> : <Link to={routes.adminPersonNew} tabIndex={-1}><Button><UserPlus />Новый сотрудник</Button></Link>} />
        ) : (
          <ul className={page.loading ? "opacity-70 transition-opacity duration-fast" : "transition-opacity duration-fast"}>
            {page.items.map((w) => (
              <motion.li key={w.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={tween.fast} className="border-t border-border first:border-t-0">
                <Link to={routes.adminPerson(w.id)} className="group flex min-w-0 items-center gap-3 px-4 py-3 transition-colors duration-fast hover:bg-muted sm:gap-4 sm:px-6">
                  <WorkerCell w={w} className="min-w-0 flex-1" />
                  <span className="hidden w-40 shrink-0 truncate text-sm text-muted-foreground md:block">{w.contractor}</span>
                  <span className="hidden sm:block"><WorkerStatusBadge w={w} inside={w.inside} /></span>
                  <span className={`size-2 shrink-0 rounded-full sm:hidden ${w.status === "blocked" ? "bg-danger" : w.inside ? "bg-success" : "bg-border-strong"}`} aria-hidden />
                  <ChevronRight className="size-4 shrink-0 text-subtle-foreground transition-transform duration-fast group-hover:translate-x-0.5" />
                </Link>
              </motion.li>
            ))}
          </ul>
        )}
        {page.ready && <LoadMore shown={page.items.length} total={page.total} hasMore={page.hasMore} loading={page.loading} error={page.error} onMore={page.more} />}
      </Card>
    </div>
  );
};
