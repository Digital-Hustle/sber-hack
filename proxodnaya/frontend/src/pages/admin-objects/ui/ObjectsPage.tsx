import { useMemo, useRef, useState } from "react";
import { Link } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { Building2, DoorOpen, Layers, Lock, MapPin, MonitorSmartphone, Pencil, Plus, Search, Trash2 } from "lucide-react";
import { useSession, can } from "@/entities/session";
import { api, useDb, presenceNow, sitesOf, siteOfZone, type Checkpoint, type CheckpointMode, type Db, type Site, type Zone } from "@/shared/api";
import { AnimatedNumber, Button, Card, Dialog, useDialogState, EmptyState, Field, Input, PageHeader, Progress, Select, Status, toast } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { cn } from "@/shared/lib";
import { fadeUp, popIn, spring, stagger } from "@/shared/config/motion";

/** Появление/удаление строки списка: остальные строки съезжают пружиной (layout), сама строка — fadeUp. */
const row = { layout: true, variants: fadeUp, initial: "hidden", animate: "show", exit: "exit", transition: { layout: spring.soft } } as const;

type Kind = "site" | "zone" | "checkpoint";
type Edit = { kind: Kind; id?: string; parentId?: string };
const MODE_OPTIONS: { value: CheckpointMode; label: string }[] = [{ value: "AUTO", label: "Вход и выход — автоматически" }, { value: "IN", label: "Только вход" }, { value: "OUT", label: "Только выход" }];
const MODE_SHORT: Record<CheckpointMode, string> = { AUTO: "вход и выход", IN: "только вход", OUT: "только выход" };
const WHAT: Record<Kind, [string, string]> = { site: ["Новый объект", "Объект"], zone: ["Новая зона", "Зона"], checkpoint: ["Новая проходная", "Проходная"] };
const msg = (e: unknown) => (e instanceof Error ? e.message : "Не получилось — попробуйте ещё раз");
const plural = (n: number, one: string, few: string, many: string) => (n % 10 === 1 && n % 100 !== 11 ? one : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20) ? few : many);

/** Создание и правка объекта, зоны или проходной: одна форма, поля зависят от вида. */
const EditDialog = ({ edit, onClose: onClosed }: { edit: Edit; onClose: () => void }) => {
  const { open, close: onClose } = useDialogState();
  const db = useDb();
  const by = useSession((x) => x.userId);
  const sites = sitesOf(db);
  const site = edit.kind === "site" && edit.id ? sites.find((s) => s.id === edit.id) : undefined;
  const zone = edit.kind === "zone" && edit.id ? db.zones.find((z) => z.id === edit.id) : undefined;
  const cp = edit.kind === "checkpoint" && edit.id ? db.checkpoints.find((c) => c.id === edit.id) : undefined;
  const [name, setName] = useState(site?.name ?? zone?.name ?? cp?.name ?? "");
  const [address, setAddress] = useState(site?.address ?? "");
  const [siteId, setSiteId] = useState(zone ? siteOfZone(db, zone.id).id : edit.parentId ?? sites[0]?.id ?? "");
  const [capacity, setCapacity] = useState(String(zone?.capacity ?? 30));
  const [zoneId, setZoneId] = useState(cp?.zoneId ?? edit.parentId ?? db.zones[0]?.id ?? "");
  const [mode, setMode] = useState<CheckpointMode>(cp?.mode ?? "AUTO");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const creating = !edit.id;
  const zoneOptions = db.zones.map((z) => ({ value: z.id, label: `${siteOfZone(db, z.id).name} · ${z.name}` }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true); setErr(null);
    try {
      if (edit.kind === "site") await (creating ? api.createSite({ name, address }, by) : api.updateSite(edit.id!, { name, address }, by));
      if (edit.kind === "zone") await (creating ? api.createZone({ siteId, name, capacity: Number(capacity) }, by) : api.updateZone(edit.id!, { siteId, name, capacity: Number(capacity) }, by));
      if (edit.kind === "checkpoint") await (creating ? api.createCheckpoint({ zoneId, name, mode }, by) : api.editCheckpoint(edit.id!, { zoneId, name, mode }, by));
      toast.success(creating ? `${WHAT[edit.kind][1]} «${name.trim()}» создан${edit.kind === "site" ? "" : "а"}` : "Изменения сохранены");
      onClose();
    } catch (x) { setErr(msg(x)); } finally { setBusy(false); }
  };

  const desc = edit.kind === "site" ? "Стройка, склад или площадка. Внутри — зоны с проходными"
    : edit.kind === "zone" ? "Часть объекта со своим допуском и вместимостью: корпус, склад, штаб"
    : "Пункт пропуска с турникетом. Ведёт в зону; терминал привязывается к проходной в «Терминалах»";
  return (
    <Dialog open={open} onClose={onClose} onClosed={onClosed} title={creating ? WHAT[edit.kind][0] : `${WHAT[edit.kind][1]}: правка`} description={desc}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        {edit.kind === "zone" && <Field label="Объект"><Select value={siteId} onChange={setSiteId} options={sites.map((s) => ({ value: s.id, label: s.name }))} /></Field>}
        {edit.kind === "checkpoint" && <Field label="Ведёт в зону" hint={cp ? "Перенос меняет, куда считается вход; журнал не меняется" : undefined}><Select value={zoneId} onChange={setZoneId} options={zoneOptions} /></Field>}
        <Field label="Название" error={err}>
          <Input autoFocus value={name} onChange={(e) => { setName(e.target.value); setErr(null); }} autoComplete="off" maxLength={80}
            placeholder={edit.kind === "site" ? "Например, ЖК «Речной»" : edit.kind === "zone" ? "Например, Корпус В" : "Например, КПП-4 · Северные ворота"} />
        </Field>
        {edit.kind === "site" && <Field label="Адрес" hint="Необязательно. Видно сотрудникам в пропуске"><Input value={address} onChange={(e) => setAddress(e.target.value)} autoComplete="off" placeholder="Улица, дом" /></Field>}
        {edit.kind === "zone" && <Field label="Вместимость, человек" hint="Сколько человек может быть в зоне одновременно — для обстановки и предупреждений"><Input type="number" inputMode="numeric" min={1} max={100000} value={capacity} onChange={(e) => setCapacity(e.target.value)} /></Field>}
        {edit.kind === "checkpoint" && <Field label="Направление" hint="Отдельные турникеты входа и выхода — фиксированный режим"><Select value={mode} onChange={setMode} options={MODE_OPTIONS} /></Field>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="quiet" onClick={onClose}>Отмена</Button>
          <Button type="submit" disabled={busy || name.trim().length < 2}>{creating ? <><Plus />Создать</> : "Сохранить"}</Button>
        </div>
      </form>
    </Dialog>
  );
};

/** Что будет при удалении — до подтверждения. Если удалять нельзя, объясняем, что сделать сначала. */
const deletePlan = (db: Db, kind: Kind, id: string): { title: string; text: string; blocked?: string } => {
  if (kind === "site") {
    const s = sitesOf(db).find((x) => x.id === id);
    const n = db.zones.filter((z) => siteOfZone(db, z.id).id === id).length;
    return { title: `Удалить объект «${s?.name}»?`, text: "Объект пропадёт из списков и пропусков сотрудников.", blocked: n ? `В объекте ${n} ${plural(n, "зона", "зоны", "зон")}. Сначала перенесите зоны в другой объект или удалите их.` : undefined };
  }
  if (kind === "zone") {
    const z = db.zones.find((x) => x.id === id);
    const u = api.zoneUsage(db, id);
    return {
      title: `Удалить зону «${z?.name}»?`,
      text: u.permits ? `Допуск в эту зону снимется у ${u.permits} ${plural(u.permits, "сотрудника", "сотрудников", "сотрудников")}. Журнал проходов сохранится.` : "Ни у кого нет допуска в эту зону. Журнал проходов сохранится.",
      blocked: u.checkpoints ? `В зоне ${u.checkpoints} ${plural(u.checkpoints, "проходная", "проходные", "проходных")}. Сначала перенесите проходные в другую зону или удалите их.` : u.inside ? `Сейчас в зоне ${u.inside} чел. Удалить можно, когда все выйдут.` : undefined,
    };
  }
  const c = db.checkpoints.find((x) => x.id === id);
  const k = api.checkpointKiosks(db, id);
  return { title: `Удалить проходную «${c?.name}»?`, text: k.length ? `${k.length} ${plural(k.length, "терминал вернётся", "терминала вернутся", "терминалов вернутся")} к коду сопряжения и перестанут пропускать: ${k.map((x) => x.name ?? "Терминал").join(", ")}. Записи журнала останутся с прежним названием.` : "Терминалов на ней нет. Записи журнала останутся с прежним названием." };
};

const DeleteDialog = ({ target, onClose: onClosed }: { target: { kind: Kind; id: string }; onClose: () => void }) => {
  const { open, close: onClose } = useDialogState();
  const db = useDb();
  const by = useSession((x) => x.userId);
  const [busy, setBusy] = useState(false);
  // После удаления диалог ещё доигрывает выход — показываем текст, каким он был до удаления.
  const live = deletePlan(db, target.kind, target.id);
  const shown = useRef(live);
  if (open && !busy) shown.current = live;
  const plan = shown.current;
  const run = async () => {
    setBusy(true);
    try {
      if (target.kind === "site") await api.deleteSite(target.id, by);
      if (target.kind === "zone") await api.deleteZone(target.id, by);
      if (target.kind === "checkpoint") await api.deleteCheckpoint(target.id, by);
      toast.success("Удалено"); onClose();
    } catch (x) { toast.error(msg(x)); setBusy(false); }
  };
  return (
    <Dialog open={open} onClose={onClose} onClosed={onClosed} title={plan.title} description={plan.blocked ?? plan.text}
      footer={plan.blocked ? <Button variant="secondary" onClick={onClose}>Понятно</Button> : <><Button variant="quiet" onClick={onClose}>Отмена</Button><Button variant="danger" disabled={busy} onClick={run}><Trash2 />Удалить</Button></>}>
      <span />
    </Dialog>
  );
};

const IconBtn = ({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) => (
  <Button variant="quiet" size="icon-sm" aria-label={label} title={label} onClick={onClick}>{children}</Button>
);

const CheckpointRow = ({ c, edit, onEdit, onDelete }: { c: Checkpoint; edit: boolean; onEdit: () => void; onDelete: () => void }) => {
  const db = useDb();
  const kiosks = api.checkpointKiosks(db, c.id).length;
  return (
    <motion.li {...row} className="flex min-w-0 items-center gap-3 py-2 pl-3 pr-1">
      <DoorOpen className="size-4 shrink-0 text-subtle-foreground" />
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-medium">{c.name}</div>
        <div className="truncate text-xs text-muted-foreground">{MODE_SHORT[c.mode ?? "AUTO"]} · {kiosks ? `${kiosks} ${plural(kiosks, "терминал", "терминала", "терминалов")}` : "без терминала"}</div>
      </div>
      {edit && <div className="flex shrink-0"><IconBtn label={`Изменить: ${c.name}`} onClick={onEdit}><Pencil /></IconBtn><IconBtn label={`Удалить: ${c.name}`} onClick={onDelete}><Trash2 /></IconBtn></div>}
    </motion.li>
  );
};

const ZoneBlock = ({ z, inside, edit, open }: { z: Zone; inside: number; edit: boolean; open: (e: Edit | { del: Kind; id: string }) => void }) => {
  const db = useDb();
  const cps = db.checkpoints.filter((c) => c.zoneId === z.id);
  const permits = db.workers.filter((w) => w.zoneIds.includes(z.id)).length;
  const load = z.capacity ? inside / z.capacity : 0;
  return (
    <motion.div {...row} className="overflow-hidden rounded-lg border border-border">
      <motion.div layout="position" className="flex flex-wrap items-center gap-3 px-3 py-3 sm:px-4">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-surface text-muted-foreground"><Layers className="size-4" /></span>
        <div className="min-w-0 flex-1">
          <div className="truncate font-medium">{z.name}</div>
          <div className="truncate text-xs text-muted-foreground">допуск у {permits} чел. · вместимость {z.capacity}</div>
        </div>
        <div className="flex w-28 flex-col gap-1">
          <span className="text-right text-xs tabular-nums text-muted-foreground">сейчас {inside} / {z.capacity}</span>
          <Progress value={load} tone={load > 1 ? "danger" : load > 0.85 ? "warning" : "primary"} />
        </div>
        {edit && <div className="flex shrink-0">
          <IconBtn label={`Добавить проходную в зону ${z.name}`} onClick={() => open({ kind: "checkpoint", parentId: z.id })}><Plus /></IconBtn>
          <IconBtn label={`Изменить: ${z.name}`} onClick={() => open({ kind: "zone", id: z.id })}><Pencil /></IconBtn>
          <IconBtn label={`Удалить: ${z.name}`} onClick={() => open({ del: "zone", id: z.id })}><Trash2 /></IconBtn>
        </div>}
      </motion.div>
      {cps.length > 0
        ? <ul className="divide-y divide-border border-t border-border"><AnimatePresence initial={false}>{cps.map((c) => <CheckpointRow key={c.id} c={c} edit={edit} onEdit={() => open({ kind: "checkpoint", id: c.id })} onDelete={() => open({ del: "checkpoint", id: c.id })} />)}</AnimatePresence></ul>
        : <p className="border-t border-border px-4 py-2.5 text-xs text-muted-foreground">Проходных нет — в зону пока не войти через терминал.{edit && <> <button type="button" className="font-medium text-foreground underline-offset-4 hover:underline" onClick={() => open({ kind: "checkpoint", parentId: z.id })}>Добавить</button></>}</p>}
    </motion.div>
  );
};

/**
 * ADR-047. Объекты → зоны → проходные. Смотрят все роли кабинета, создаёт, меняет и удаляет только администратор
 * (проверка и на «сервере»). Удаление не ломает историю: журнал и табель остаются.
 */
export const ObjectsPage = () => {
  const db = useDb();
  const role = useSession((x) => x.role);
  const edit = can(role, "manageObjects");
  const [q, setQ] = useState("");
  const [dialog, setDialog] = useState<Edit | null>(null);
  const [del, setDel] = useState<{ kind: Kind; id: string } | null>(null);
  const open = (e: Edit | { del: Kind; id: string }) => ("del" in e ? setDel({ kind: e.del, id: e.id }) : setDialog(e));

  const inside = useMemo(() => {
    const m = new Map<string, number>();
    for (const p of presenceNow(db)) m.set(p.zoneId, (m.get(p.zoneId) ?? 0) + 1);
    return m;
  }, [db]);
  const groups = useMemo(() => {
    const s = q.trim().toLocaleLowerCase("ru");
    const hit = (t?: string) => !s || !!t?.toLocaleLowerCase("ru").includes(s);
    const sites: Site[] = [...sitesOf(db)];
    // Все объекты удалены, а зоны остались (база до ADR-044) — показываем их отдельной группой «Без объекта».
    const orphan = db.zones.find((z) => !sites.some((x) => x.id === siteOfZone(db, z.id).id));
    if (orphan) sites.push(siteOfZone(db, orphan.id));
    return sites.map((site) => {
      const zones = db.zones.filter((z) => siteOfZone(db, z.id).id === site.id);
      const siteHit = hit(site.name) || hit(site.address);
      const shown = siteHit ? zones : zones.filter((z) => hit(z.name) || db.checkpoints.some((c) => c.zoneId === z.id && hit(c.name)));
      return { site, zones: shown, total: zones.length, visible: siteHit || shown.length > 0 };
    }).filter((g) => g.visible);
  }, [db, q]);
  const paired = (db.kiosks ?? []).filter((k) => k.pairedAt).length;
  const tiles = [
    { label: plural(sitesOf(db).length, "объект", "объекта", "объектов"), n: sitesOf(db).length, icon: Building2 },
    { label: plural(db.zones.length, "зона", "зоны", "зон"), n: db.zones.length, icon: Layers },
    { label: plural(db.checkpoints.length, "проходная", "проходные", "проходных"), n: db.checkpoints.length, icon: DoorOpen },
    { label: plural(paired, "терминал привязан", "терминала привязано", "терминалов привязано"), n: paired, icon: MonitorSmartphone },
  ];

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader title="Объекты" sub="Стройки и склады, их зоны и проходные. Сотрудник получает допуск в зоны, терминал привязывается к проходной"
        actions={edit ? <>
          <Button variant="secondary" disabled={!db.zones.length} onClick={() => setDialog({ kind: "checkpoint" })}><DoorOpen />Проходная</Button>
          <Button variant="secondary" disabled={!sitesOf(db).length} onClick={() => setDialog({ kind: "zone" })}><Layers />Зона</Button>
          <Button onClick={() => setDialog({ kind: "site" })}><Plus />Объект</Button>
        </> : undefined} />
      <motion.div variants={stagger()} initial="hidden" animate="show" className="flex flex-col gap-4">
        <motion.div variants={stagger()} className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {tiles.map(({ label, n, icon: Icon }) => (
            <motion.div key={label} variants={popIn} className="flex flex-col gap-1 rounded-xl bg-card px-4 py-3 shadow-card">
              <span className="flex items-center gap-2 font-display text-2xl font-semibold tabular-nums"><Icon className="size-4 text-subtle-foreground" /><AnimatedNumber value={n} /></span>
              <span className="text-sm leading-tight text-muted-foreground">{label}</span>
            </motion.div>
          ))}
        </motion.div>
        {!edit && (
          <motion.div variants={fadeUp} className="flex items-center gap-3 rounded-lg bg-muted px-4 py-3 text-sm text-muted-foreground">
            <Lock className="size-4 shrink-0" />Только просмотр. Создавать, менять и удалять объекты, зоны и проходные может администратор.
          </motion.div>
        )}
        {(db.zones.length > 3 || sitesOf(db).length > 3) && (
          <motion.div variants={fadeUp}><Input icon={<Search />} value={q} onChange={(e) => setQ(e.target.value)} placeholder="Объект, адрес, зона или проходная" aria-label="Поиск" className="bg-card" /></motion.div>
        )}
        {groups.length === 0 ? (
          <motion.div variants={fadeUp}><Card><EmptyState icon={<Building2 />} title={q ? "Ничего не нашли" : "Объектов пока нет"} text={q ? "Измените запрос" : "Создайте объект, затем зоны и проходные в нём"}
            action={edit && !q ? <Button onClick={() => setDialog({ kind: "site" })}><Plus />Объект</Button> : undefined} /></Card></motion.div>
        ) : <AnimatePresence initial={false}>{groups.map(({ site, zones, total }) => (
          <motion.div key={site.id || "none"} layout variants={fadeUp} exit="exit" transition={{ layout: spring.soft }}><Card>
            <div className="flex flex-wrap items-center gap-3 border-b border-border px-4 py-4 sm:px-6">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-accent text-accent-foreground"><Building2 className="size-5" /></span>
              <div className="min-w-0 flex-1">
                <h2 className="truncate font-display text-lg font-semibold tracking-display">{site.name}</h2>
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
                  {site.address && <span className="flex items-center gap-1"><MapPin className="size-3.5" />{site.address}</span>}
                  <span>{total} {plural(total, "зона", "зоны", "зон")}</span>
                </div>
              </div>
              {edit && site.id && <div className="flex shrink-0 gap-1">
                <Button variant="secondary" size="sm" onClick={() => setDialog({ kind: "zone", parentId: site.id })}><Plus />Зона</Button>
                <IconBtn label={`Изменить: ${site.name}`} onClick={() => setDialog({ kind: "site", id: site.id })}><Pencil /></IconBtn>
                <IconBtn label={`Удалить: ${site.name}`} onClick={() => setDel({ kind: "site", id: site.id })}><Trash2 /></IconBtn>
              </div>}
              {!site.id && <Status tone="warning">зоны без объекта — перенесите их</Status>}
            </div>
            <div className={cn("flex flex-col gap-3 p-4 sm:p-6", !zones.length && "py-5")}>
              {zones.length ? <AnimatePresence initial={false}>{zones.map((z) => <ZoneBlock key={z.id} z={z} inside={inside.get(z.id) ?? 0} edit={edit} open={open} />)}</AnimatePresence>
                : <p className="text-sm text-muted-foreground">Зон нет.{edit && site.id && <> <button type="button" className="font-medium text-foreground underline-offset-4 hover:underline" onClick={() => setDialog({ kind: "zone", parentId: site.id })}>Добавить зону</button></>}</p>}
            </div>
          </Card></motion.div>
        ))}</AnimatePresence>}
        <motion.p variants={fadeUp} className="text-pretty text-sm text-muted-foreground">
          Допуски людей в зоны — в карточке сотрудника, терминалы на проходных — в разделе{" "}
          <Link to={routes.adminTerminals} className="font-medium text-foreground underline-offset-4 hover:underline">Терминалы</Link>. Все изменения пишутся в журнал раздела «Доступ».
        </motion.p>
      </motion.div>
      {dialog && <EditDialog edit={dialog} onClose={() => setDialog(null)} />}
      {del && <DeleteDialog target={del} onClose={() => setDel(null)} />}
    </div>
  );
};
