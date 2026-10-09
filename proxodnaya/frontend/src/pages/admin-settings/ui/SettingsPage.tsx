import { motion } from "motion/react";
import { api, useDb, FACE_FIRST_MARGIN, type Settings, type CheckpointMode, type TerminalMode, type OfflinePolicy, type TerminalScope } from "@/shared/api";
import { Check, Minus, ShieldAlert } from "lucide-react";
import { useSession, can, ROLES, PERMS } from "@/entities/session";
import { cn } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { Link } from "react-router";
import { Badge, Card, CardHeader, CardTitle, Field, Input, SwitchRow, PageHeader, Select } from "@/shared/ui";
import { fadeUp, stagger } from "@/shared/config/motion";


const MODES: { v: TerminalMode; title: string; badge?: string; text: string }[] = [
  { v: "QR_FACE", title: "QR + лицо", badge: "Рекомендуется", text: "Сотрудник показывает QR, терминал сверяет лицо с фото владельца пропуска и просит простое действие для проверки живости. Два независимых фактора — так требует положение хакатона." },
  { v: "FACE_FIRST", title: "Сначала лицо", text: "Достаточно посмотреть в камеру: сервер ищет человека по базе со строгим порогом и проверкой живости. Не узнал — терминал просит QR и сверяет лицо уже по нему. Быстрее в час пик, но один фактор вместо двух." },
  { v: "QR_ONLY", title: "Только QR", text: "Быстрее всего: подпись, 30-секундное окно, одноразовость кода и правила допуска — без сверки лица. Подходит для внутренних турникетов и пиковых смен." },
];
const OFFLINE: { v: OfflinePolicy; title: string; badge?: string; text: string }[] = [
  { v: "GUARD", title: "Пропуск охранником", badge: "Рекомендуется", text: "Терминал не пропускает сам. Охранник проверяет личность, вводит свой код и причину; запись помечается как ручная и уходит в журнал, когда связь вернётся." },
  { v: "CLOSED", title: "Проход закрыт", text: "До восстановления связи терминал никого не пропускает. Строже всего, но у турникета соберётся очередь." },
  { v: "LOCAL", title: "Автономная проверка QR", text: "Терминал сам проверяет подпись QR по снимку допусков: закрытый ключ — в телефоне, открытые ключи и списки — на терминале. Пропускает в пределах срока, проходы уходят на сервер, когда связь вернётся." },
];

const SCOPES: { v: TerminalScope; title: string; badge?: string; text: string }[] = [
  { v: "GLOBAL", title: "Одна для всех терминалов", badge: "По умолчанию", text: "Логика и поведение без связи задаются здесь и сразу действуют на всех киосках объекта." },
  { v: "PER_KIOSK", title: "Своя у каждого терминала", text: "Здесь — значение по умолчанию, а в «Терминалах» каждому киоску можно задать свою логику и поведение без связи." },
];

const Choice = ({ active, onClick, title, text, badge }: { active: boolean; onClick: () => void; title: string; text: string; badge?: string }) => (
  <motion.button type="button" role="radio" aria-checked={active} onClick={onClick} whileTap={{ scale: 0.98 }}
    className={cn("flex flex-col gap-1.5 rounded-lg border p-4 text-left outline-none transition-colors duration-fast focus-visible:ring-2 focus-visible:ring-ring", active ? "border-primary bg-accent text-accent-foreground" : "border-border hover:bg-surface")}>
    <span className="flex flex-wrap items-center gap-2 font-medium">{title}{badge && <Badge>{badge}</Badge>}</span>
    <span className={cn("text-pretty text-sm", active ? "opacity-80" : "text-muted-foreground")}>{text}</span>
  </motion.button>
);

export const SettingsPage = () => {
  const { settings: s, checkpoints } = useDb();
  const role = useSession((x) => x.role);
  const set = (patch: Partial<Settings>) => api.updateSettings(patch);
  const perKiosk = (s.terminalScope ?? "GLOBAL") === "PER_KIOSK";
  const local = (s.offlinePolicy ?? "GUARD") === "LOCAL";
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Настройки" sub="Логика терминалов, правила прохода и роли" />
      <motion.div variants={stagger()} initial="hidden" animate="show" className="flex flex-col gap-3 sm:gap-4">
        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Логика терминала</CardTitle></CardHeader>
          <div className="flex flex-col gap-6 p-4 sm:p-6">
            <div className="flex flex-col gap-3">
              <div><div className="font-medium">Где задаётся логика</div><p className="text-sm text-muted-foreground">Например, «Сначала лицо» на потоковом турникете и «QR + лицо» на складе — тогда нужна настройка по терминалам</p></div>
              <div role="radiogroup" aria-label="Где задаётся логика" className="grid gap-3 sm:grid-cols-2">
                {SCOPES.map((x) => <Choice key={x.v} active={(s.terminalScope ?? "GLOBAL") === x.v} onClick={() => set({ terminalScope: x.v })} title={x.title} text={x.text} badge={x.badge} />)}
              </div>
              {perKiosk && <Link to={routes.adminTerminals} className="self-start text-sm font-medium underline-offset-4 hover:underline">Настроить терминалы →</Link>}
            </div>
            <div className="flex flex-col gap-3">
              <div><div className="font-medium">{perKiosk ? "Как сотрудник проходит · по умолчанию" : "Как сотрудник проходит"}</div><p className="text-sm text-muted-foreground">{perKiosk ? "Для новых терминалов и тех, кому не задана своя логика" : "Действует на всех терминалах объекта"}</p></div>
              <div role="radiogroup" aria-label="Логика терминала" className="grid gap-3 sm:grid-cols-3">
                {MODES.map((x) => <Choice key={x.v} active={(s.terminalMode ?? "QR_FACE") === x.v} onClick={() => set({ terminalMode: x.v })} title={x.title} text={x.text} badge={x.badge} />)}
              </div>
              {s.terminalMode === "FACE_FIRST" && <p className="text-sm text-muted-foreground">Порог узнавания по базе: {(Math.min(0.95, s.faceThreshold + FACE_FIRST_MARGIN)).toFixed(2)} — строже, чем для сверки по QR ({s.faceThreshold.toFixed(2)}), чтобы не перепутать похожих людей.</p>}
            </div>
            <div className="flex flex-col gap-3">
              <div><div className="font-medium">Если нет связи с сервером</div><p className="text-sm text-muted-foreground">{local ? "Терминал проверяет QR сам — по снимку допусков, скачанному при последней связи." : "Решение принимает охранник, или проход закрыт до восстановления связи."}</p></div>
              <div role="radiogroup" aria-label="Поведение без связи" className="grid gap-3 sm:grid-cols-3">
                {OFFLINE.map((x) => <Choice key={x.v} active={(s.offlinePolicy ?? "GUARD") === x.v} onClick={() => set({ offlinePolicy: x.v })} title={x.title} text={x.text} badge={x.badge} />)}
              </div>
              {(local || perKiosk) && (
                <div className="grid gap-5 rounded-lg border border-border p-4 sm:grid-cols-2">
                  <Field label="Срок автономной работы, ч" hint="Сколько терминал пропускает сам после последней связи"><Input type="number" inputMode="numeric" step="1" min="1" max="72" value={s.offlineMaxHours ?? 12} onChange={(e) => set({ offlineMaxHours: Math.min(72, Math.max(1, Number(e.target.value) || 1)) })} /></Field>
                  <Field label="Когда срок истёк" hint="Дальше терминал сам не пропускает"><Select value={s.offlineAfterExpiry ?? "GUARD"} onChange={(v) => set({ offlineAfterExpiry: v as "GUARD" | "CLOSED" })} aria-label="Когда срок истёк" options={[{ value: "GUARD", label: "Пропуск охранником" }, { value: "CLOSED", label: "Проход закрыт" }]} /></Field>
                  <SwitchRow title="Проверять смену без связи" text="По графику из снимка, если включено «Пускать только по смене»" checked={s.offlineCheckShift ?? true} onChange={(v) => set({ offlineCheckShift: v })} />
                  <p className="text-pretty text-sm text-muted-foreground sm:col-span-2">Терминал обновляет снимок раз в минуту, пока есть связь. Проверить можно на киоске: сервисная панель → «Имитация обрыва связи».</p>
                </div>
              )}
            </div>
            <div className="flex gap-3 rounded-lg bg-surface p-4 text-sm">
              <ShieldAlert className="mt-0.5 size-5 shrink-0 text-warning" />
              <div className="flex flex-col gap-1.5">
                <span className="font-medium">Чем рискует проход по одному QR</span>
                <span className="text-pretty text-muted-foreground">Без сверки лица терминал проверяет подпись, 30-секундное окно и одноразовость кода: скриншот чужого пропуска, переданный за эти секунды, откроет турникет. С связью одноразовость гарантирует сервер, без связи — только этот терминал. Поэтому каждый автономный проход помечается «без связи» и при синхронизации сверяется с сервером: отвязанные телефоны и коды, погашенные на другом терминале, отмечаются как конфликт.</span>
              </div>
            </div>
          </div>
        </Card></motion.div>
        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Проверка прохода</CardTitle></CardHeader>
          <div className="grid gap-5 p-4 sm:grid-cols-2 sm:p-6">
            <Field label="Порог сходства лица τ" hint="Проход разрешается при сходстве ≥ τ"><Input type="number" inputMode="decimal" step="0.05" min="0.3" max="0.95" value={s.faceThreshold} onChange={(e) => set({ faceThreshold: Number(e.target.value) })} /></Field>
            <Field label="Допуск часов QR, с" hint="Допустимое расхождение часов устройства и киоска (окно кода — 30 с)"><Input type="number" inputMode="numeric" step="15" min="30" max="180" value={s.qrToleranceSec} onChange={(e) => set({ qrToleranceSec: Number(e.target.value) })} /></Field>
            <Field label="Допуск к смене, мин" hint="За сколько минут до начала смены разрешён вход"><Input type="number" inputMode="numeric" step="15" min="0" max="180" value={s.shiftGraceMin} onChange={(e) => set({ shiftGraceMin: Number(e.target.value) })} /></Field>
            <div className="self-end rounded-md border border-border px-3"><SwitchRow title="Пускать только по смене" text="Сотрудник без смены на сегодня не допускается" checked={s.requireShift} onChange={(v) => set({ requireShift: v })} /></div>
          </div>
        </Card></motion.div>
        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Определение направления</CardTitle></CardHeader>
          <div className="flex flex-col gap-5 p-4 sm:p-6">
            <p className="text-pretty text-sm text-muted-foreground">Киоск не спрашивает, вход это или выход: направление определяет система по текущему присутствию сотрудника (ADR-037). Для турникетов, работающих только на вход или только на выход, задайте фиксированный режим.</p>
            <div className="grid gap-5 sm:grid-cols-2">
              <Field label="Защита от повторного скана, с" hint="Повторное предъявление в этот период отклоняется с кодом REPEAT_SCAN"><Input type="number" inputMode="numeric" step="5" min="5" max="300" value={s.repeatScanCooldownSec ?? 30} onChange={(e) => set({ repeatScanCooldownSec: Number(e.target.value) })} /></Field>
              <Field label="Незакрытое присутствие, ч" hint="Если выход не отмечен дольше этого срока, следующий скан считается входом"><Input type="number" inputMode="numeric" step="1" min="4" max="48" value={s.presenceTtlHours ?? 16} onChange={(e) => set({ presenceTtlHours: Number(e.target.value) })} /></Field>
            </div>
            <div className="flex flex-col divide-y divide-border rounded-md border border-border">
              {checkpoints.map((c) => (
                <div key={c.id} className="flex flex-wrap items-center justify-between gap-3 px-3 py-2.5">
                  <span className="text-sm font-medium">{c.name}</span>
                  <Select size="sm" value={c.mode ?? "AUTO"} onChange={(v) => api.updateCheckpoint(c.id, { mode: v as CheckpointMode })} aria-label={`Режим: ${c.name}`} className="w-auto"
                    options={[{ value: "AUTO", label: "Автоматически" }, { value: "IN", label: "Только вход" }, { value: "OUT", label: "Только выход" }]} />
                </div>
              ))}
            </div>
          </div>
        </Card></motion.div>
        <motion.div variants={fadeUp}><Card>
          <CardHeader><CardTitle>Роли и доступ</CardTitle></CardHeader>
          <div className="flex flex-col gap-4 p-4 sm:p-6">
            <p className="text-pretty text-sm text-muted-foreground">Каждая роль видит только свои разделы. Инженер терминалов подключает киоски, но не видит персональные данные.</p>
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="w-full text-sm">
                <thead className="bg-muted text-left text-muted-foreground"><tr><th className="px-3 py-2 font-medium">Роль</th>{PERMS.map((p) => <th key={p.id} className="whitespace-nowrap px-2 py-2 text-center font-medium">{p.label}</th>)}</tr></thead>
                <tbody>{ROLES.map((r) => (
                  <tr key={r.id} className={cn("border-t border-border", r.id === role && "bg-accent text-accent-foreground")}>
                    <td className="px-3 py-2.5"><div className="whitespace-nowrap font-medium">{r.label}</div><div className="hidden text-xs text-muted-foreground md:block">{r.text}</div></td>
                    {PERMS.map((p) => <td key={p.id} className="px-2 py-2.5 text-center">{can(r.id, p.id) ? <Check className="mx-auto size-4 text-success" aria-label="есть" /> : <Minus className="mx-auto size-4 text-subtle-foreground" aria-label="нет" />}</td>)}
                  </tr>
                ))}</tbody>
              </table>
            </div>
            {can(role, "access") && <Link to={routes.adminAccess} className="self-start text-sm font-medium underline-offset-4 hover:underline">Назначить роли людям →</Link>}
          </div>
        </Card></motion.div>
      </motion.div>
    </div>
  );
};
