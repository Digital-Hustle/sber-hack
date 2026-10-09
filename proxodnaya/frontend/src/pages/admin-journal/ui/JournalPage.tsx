import { Fragment, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { Download, ScrollText, Search, ShieldCheck } from "lucide-react";
import { useSession, can, roleLabel } from "@/entities/session";
import { api, useDb, REASONS, SYSTEM_CODES, type Decision, type Attempt } from "@/shared/api";
import { Status, Button, Card, Dialog, useDialogState, EmptyState, Field, Input, Select, PageHeader, LoadMore, RowsSkeleton, toast } from "@/shared/ui";
import { usePaged, useDebounced } from "@/shared/hooks";
import { motion } from "motion/react";
import { AttemptRow, DecisionBadge, decisionView, directionSource } from "@/entities/pass";
import { fadeUp, stagger } from "@/shared/config/motion";
import { dayKey, hhmmss, dateRu, todayKey } from "@/shared/lib";

const csv = (rows: (string | number)[][]) => rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(";")).join("\n");

/** Отметка ручного пропуска (ADR-040): ждёт / подтверждён / оспорен. Нажатие открывает подробности. */
const ReviewMark = ({ a, onOpen }: { a: Attempt; onOpen: (a: Attempt) => void }) => (
  <button type="button" onClick={() => onOpen(a)} className="rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring">
    {!a.review ? <Status tone="warning">ждёт подтверждения</Status> : a.review.status === "CONFIRMED" ? <Status tone="success">подтверждён</Status> : <Status tone="danger">оспорен</Status>}
  </button>
);

const ReviewDialog = ({ a, who, where, onClose: onClosed }: { a: Attempt; who: string; where: string; onClose: () => void }) => {
  const { open, close: onClose } = useDialogState();
  const role = useSession((x) => x.role);
  const userId = useSession((x) => x.userId);
  const me = useDb().admins?.find((u) => u.id === userId);
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const allowed = can(role, "reviewManual") && !a.review;
  const act = async (status: "CONFIRMED" | "DISPUTED") => {
    setBusy(true);
    try {
      await api.reviewManual(a.id, status, me ? `${me.name}, ${roleLabel(role)}` : roleLabel(role), comment);
      toast.success(status === "CONFIRMED" ? "Ручной пропуск подтверждён" : "Пропуск оспорен — нужен разбор инцидента");
      onClose();
    } catch (e) { toast.error(e instanceof Error ? e.message : "Не удалось сохранить"); } finally { setBusy(false); }
  };
  const rows: [string, string][] = [
    ["Сотрудник", who], ["Когда", `${dateRu(a.ts)}, ${hhmmss(a.ts)}`], ["Проходная", where],
    ["Пропустил", a.guard ?? "Охранник поста"], ["Причина", a.note ?? "—"],
  ];
  if (a.review) {
    rows.push([a.review.status === "CONFIRMED" ? "Подтвердил" : "Оспорил", `${a.review.by}, ${dateRu(a.review.at)} ${hhmmss(a.review.at)}`]);
    if (a.review.comment) rows.push(["Комментарий", a.review.comment]);
  }
  return (
    <Dialog open={open} onClose={onClose} onClosed={onClosed} title="Ручной пропуск"
      description={a.review ? "Проверка завершена и больше не меняется" : "Решение охранника действует сразу, но остаётся на проверке, пока его не подтвердит второй человек"}
      footer={allowed ? <><Button variant="danger-soft" disabled={busy || !comment.trim()} onClick={() => act("DISPUTED")}>Оспорить</Button><Button disabled={busy} onClick={() => act("CONFIRMED")}><ShieldCheck />Подтвердить</Button></> : <Button variant="secondary" onClick={onClose}>Закрыть</Button>}>
      <div className="flex flex-col gap-5">
        <dl className="grid grid-cols-3 gap-x-4 gap-y-2.5 text-sm">
          {rows.map(([k, v]) => <Fragment key={k}><dt className="text-muted-foreground">{k}</dt><dd className="col-span-2 min-w-0 break-words">{v}</dd></Fragment>)}
        </dl>
        {allowed && <Field label="Комментарий" hint="Обязателен, если оспариваете"><Input value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Например: сверил по видео с камеры у турникета" /></Field>}
        {!allowed && !a.review && <p className="text-pretty text-sm text-muted-foreground">Подтвердить может служба безопасности или администратор — охранник не проверяет сам себя.</p>}
      </div>
    </Dialog>
  );
};

export const JournalPage = () => {
  const db = useDb();
  const [sp, setSp] = useSearchParams();
  const set = (k: string, v: string) => setSp((p) => { if (v) p.set(k, v); else p.delete(k); return p; }, { replace: true });
  const decision = sp.get("decision") ?? "";
  const direction = sp.get("dir") ?? "";
  const day = sp.get("day") ?? todayKey();
  const q = sp.get("q") ?? "";
  const dq = useDebounced(q);
  const names = useMemo(() => new Map(db.workers.map((w) => [w.id, w.fullName])), [db.workers]);
  const name = (id?: string) => (id && names.get(id)) || "Неизвестный пропуск";
  const cp = (id: string) => api.checkpointName(db, id);
  const src = (id: string) => directionSource(db.checkpoints.find((c) => c.id === id));

  const checkpoint = sp.get("cp") ?? "";
  const reviewOnly = sp.get("review") === "pending";
  const [open, setOpen] = useState<Attempt | null>(null);
  const pending = useMemo(() => db.attempts.filter((a) => a.decision === "MANUAL" && !a.review).length, [db.attempts]);
  const reviewed = db.attempts.length - pending;
  const filters = { day: reviewOnly ? "all" : day, decision, direction, checkpointId: checkpoint || undefined, q: dq, review: reviewOnly ? ("PENDING" as const) : undefined };
  const page = usePaged((cursor, limit) => api.queryAttempts({ ...filters, cursor, limit }), JSON.stringify(filters), { live: `${db.attempts.length}:${reviewed}`, id: (a) => a.id, pageSize: 50 });

  const days = useMemo(() => [...new Set(db.attempts.map((a) => dayKey(a.ts)))].sort().reverse(), [db]);
  const download = async () => {
    const list = await api.exportAttempts(filters);
    if (!list.length) { toast.info("Нечего выгружать — под фильтр не попало ни одной записи"); return; }
    const body = csv([["Дата", "Время", "Сотрудник", "Проходная", "Направление", "Источник направления", "Решение", "Код", "Причина", "Сходство"], ...list.map((a) => [dayKey(a.ts), hhmmss(a.ts), name(a.workerId), cp(a.checkpointId), a.direction === "IN" ? "вход" : "выход", src(a.checkpointId), a.decision, a.code, REASONS[a.code].message, a.score ? a.score.toFixed(2) : ""])]);
    const url = URL.createObjectURL(new Blob(["\uFEFF" + body], { type: "text/csv;charset=utf-8" }));
    Object.assign(document.createElement("a"), { href: url, download: `journal-${day}.csv` }).click();
    URL.revokeObjectURL(url);
  };

  return (
    <div>
      <PageHeader title="Журнал проходов" sub="Все попытки прохода с решением и причиной. Фильтры сохраняются в адресе страницы" actions={<>{(pending > 0 || reviewOnly) && <Button variant={reviewOnly ? "primary" : "secondary"} onClick={() => set("review", reviewOnly ? "" : "pending")}><ShieldCheck />{reviewOnly ? "Показать все" : `На проверке · ${pending}`}</Button>}<Button variant="secondary" onClick={download}><Download />Скачать CSV</Button></>} />
      <div className="mb-3 grid gap-2 sm:mb-4 sm:grid-cols-2 lg:grid-cols-6">
        <div className="min-w-0 sm:col-span-2 lg:col-span-2"><Input icon={<Search />} value={q} onChange={(e) => set("q", e.target.value)} placeholder="Сотрудник или причина" aria-label="Поиск" className="bg-card" /></div>
          <Select aria-label="День" value={day} onChange={(v) => set("day", v)} className="bg-card"
            options={[{ value: "all", label: "Все дни" }, ...days.map((d) => ({ value: d, label: d === todayKey() ? "Сегодня" : dateRu(new Date(d).getTime()) }))]} />
          <Select aria-label="Решение" value={decision} onChange={(v) => set("decision", v)} className="bg-card"
            options={[{ value: "", label: "Все решения" }, ...(["ALLOW", "DENY", "MANUAL", "ERROR"] as Decision[]).map((d) => ({ value: d, label: decisionView[d].label }))]} />
          <Select aria-label="Направление" value={direction} onChange={(v) => set("dir", v)} className="bg-card"
            options={[{ value: "", label: "Вход и выход" }, { value: "IN", label: "Только вход" }, { value: "OUT", label: "Только выход" }]} />
          <Select aria-label="Проходная" value={checkpoint} onChange={(v) => set("cp", v)} className="bg-card"
            options={[{ value: "", label: "Все проходные" }, ...db.checkpoints.map((c) => ({ value: c.id, label: c.name }))]} />
      </div>
      <motion.div variants={fadeUp} initial="hidden" animate="show"><Card className="overflow-hidden">
        {!page.ready ? <RowsSkeleton /> : page.items.length === 0 && !page.error ? <EmptyState icon={<ScrollText />} title="Записей нет" text="Попробуйте другой день или сбросьте фильтры" /> : (<>
          <div className="hidden overflow-x-auto lg:block">
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-muted-foreground"><tr className="border-b border-border">{["Время", "Сотрудник", "Проходная", "Направление", "Решение", "Причина", "Сходство"].map((h) => <th key={h} className="whitespace-nowrap px-4 py-3 font-medium first:pl-6 last:pr-6">{h}</th>)}</tr></thead>
              <motion.tbody key={`${day}${decision}${direction}${checkpoint}${dq}`} variants={stagger(0.015)} initial="hidden" animate="show">
                {page.items.map((a, i) => (
                  <motion.tr key={a.id} variants={i < 30 ? fadeUp : undefined} className="border-b border-border transition-colors duration-fast last:border-0 hover:bg-muted">
                    <td className="whitespace-nowrap px-4 py-3 pl-6 tabular-nums text-muted-foreground">{day === "all" ? `${dateRu(a.ts)} ` : ""}{hhmmss(a.ts)}</td>
                    <td className="px-4 py-3 font-medium">{name(a.workerId)}</td>
                    <td className="px-4 py-3 text-muted-foreground">{cp(a.checkpointId)}</td>
                    <td className="px-4 py-3"><div>{a.direction === "IN" ? "вход" : "выход"}</div><div className="whitespace-nowrap text-xs text-muted-foreground">{src(a.checkpointId)}</div></td>
                    <td className="px-4 py-3"><div className="flex flex-col items-start gap-1.5"><DecisionBadge decision={a.decision} code={a.code} />{a.decision === "MANUAL" && <ReviewMark a={a} onOpen={setOpen} />}</div></td>
                    <td className="px-4 py-3">{a.decision === "ALLOW" ? <span className="text-subtle-foreground">—</span> : <span className="flex flex-wrap items-center gap-2">{REASONS[a.code].message}{SYSTEM_CODES.has(a.code) && <Status tone="info">системный</Status>}</span>}{a.note && <span className="text-muted-foreground"> · {a.note}</span>}</td>
                    <td className="px-4 py-3 pr-6 tabular-nums text-muted-foreground">{a.score ? `${Math.round(a.score * 100)}%` : ""}</td>
                  </motion.tr>
                ))}
              </motion.tbody>
            </table>
          </div>
          <div className="divide-y divide-border px-4 sm:px-6 lg:hidden">{page.items.map((a) => <AttemptRow key={a.id} a={a} showDate={day === "all"} dirSource={src(a.checkpointId)} who={<div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1"><span className="truncate text-sm font-medium">{name(a.workerId)}</span>{a.decision === "MANUAL" && <ReviewMark a={a} onOpen={setOpen} />}</div>} />)}</div>
          <LoadMore shown={page.items.length} total={page.total} hasMore={page.hasMore} loading={page.loading} error={page.error} onMore={page.more} />
        </>)}
      </Card></motion.div>
      {open && <ReviewDialog a={open} who={name(open.workerId)} where={cp(open.checkpointId)} onClose={() => setOpen(null)} />}
    </div>
  );
};
