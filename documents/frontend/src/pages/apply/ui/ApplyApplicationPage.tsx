import { useMemo, useState } from "react";
import { useLocation, useParams, Link } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { Copy, Link2Off, MessageSquareWarning, PartyPopper, Undo2, Pencil, ShieldCheck } from "lucide-react";
import { applicantApi, applicantKeys, AppStatusBadge, dateTimeRu } from "@/entities/application";
import { ProfileForm } from "@/features/fill-profile";
import { ResumeForm } from "@/features/start-application";
import { SubmitButton } from "@/features/submit-application";
import { DocumentChecklist } from "@/widgets/document-checklist";
import { Button, Card, EmptyState, PageHeader, RowsSkeleton, toast } from "@/shared/ui";
import { ApiError, errorText, setAuth, useEventStream } from "@/shared/api";
import { applicationKey, applicationLink } from "@/shared/lib";
import { routes } from "@/shared/const/router";
import { fadeUp } from "@/shared/config/motion";

/** Ключ заявки → Authorization: Applicant <key> для всех запросов кабинета (ставим до первого запроса). */
const useApplicantAuth = (id: string) => {
  const key = useMemo(() => applicationKey.captureFromHash(id), [id]);
  if (key) setAuth(() => `Applicant ${key}`);
  return key;
};

const KeyCard = ({ id, resumeCode }: { id: string; resumeCode?: string }) => {
  const key = applicationKey.get(id);
  const [hidden, setHidden] = useState(false);
  if (hidden || !key) return null;
  const copy = async () => {
    try { await navigator.clipboard.writeText(applicationLink(id, key)); toast.success("Ссылка скопирована"); } catch { toast.error("Не удалось скопировать"); }
  };
  return (
    <motion.div variants={fadeUp} initial="hidden" animate="show">
      <Card className="mb-4 border-primary/30 bg-accent p-4 text-accent-foreground sm:p-5">
        <div className="flex items-start gap-3">
          <ShieldCheck className="mt-0.5 size-5 shrink-0" />
          <div className="min-w-0 flex-1 text-sm">
            <p className="font-semibold">Сохраните ссылку на заявку</p>
            <p className="mt-1 opacity-90">По ней можно вернуться с этого или другого устройства. Никому её не пересылайте.</p>
            {resumeCode && <p className="mt-2">Код возобновления: <b className="font-mono text-base tracking-widest">{resumeCode}</b> — запишите, он показывается один раз</p>}
          </div>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button size="sm" onClick={copy}><Copy />Скопировать ссылку</Button>
          <Button size="sm" variant="quiet" onClick={() => setHidden(true)}>Сохранил</Button>
        </div>
      </Card>
    </motion.div>
  );
};

/** /apply/:id — анкета → список → загрузка → стадии по SSE → итог (FE-AP-02..07). */
export const ApplyApplicationPage = () => {
  const { id = "" } = useParams();
  const loc = useLocation();
  const fresh = loc.state as { resumeCode?: string; fresh?: boolean } | null;
  const key = useApplicantAuth(id);
  const qc = useQueryClient();
  const [editProfile, setEditProfile] = useState(false);
  const q = useQuery({ queryKey: applicantKeys.application(id), queryFn: () => applicantApi.get(id), enabled: !!key, retry: (n, e) => !(e instanceof ApiError && e.status < 500) && n < 2 });
  useEventStream("intake", key ? `/applicant/applications/${id}/stream/ticket` : null, `/applicant/applications/${id}/stream`,
    ["document.stage", "application.changed", "application.request", "application.decision"],
    () => { void qc.invalidateQueries({ queryKey: applicantKeys.application(id) }); });

  const app = q.data;
  const titles = useMemo(() => new Map((app?.requiredDocuments ?? []).map((r) => [r.type!, r.title ?? r.type!])), [app]);
  const titleOf = (t?: string) => (t ? titles.get(t) ?? t : "документ");

  if (!key || (q.error instanceof ApiError && (q.error.status === 401 || q.error.status === 404))) {
    return (
      <Card className="p-4 sm:p-6">
        <EmptyState icon={<Link2Off />} title="Ссылка недействительна" text="Откройте ссылку из сообщения целиком или войдите по коду возобновления" className="pb-6" />
        <ResumeForm />
      </Card>
    );
  }
  if (q.isPending) return <RowsSkeleton rows={5} />;
  if (q.isError || !app) return <EmptyState title="Не удалось загрузить заявку" text={errorText(q.error)} action={<Button variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />;

  const hasProfile = app.profile && Object.keys(app.profile).length > 0;
  const missing = app.missing ?? [];
  const openRequests = (app.requests ?? []).filter((r) => !r.resolved);
  const decided = app.status === "ACCEPTED" || app.status === "RETURNED";

  return (
    <>
      <PageHeader kicker={<span className="flex items-center gap-2">Заявка от {dateTimeRu(app.createdAt)} <AppStatusBadge status={app.status} /></span>}
        title={!hasProfile || editProfile ? "Пара вопросов о вас" : "Ваши документы"}
        sub={!hasProfile || editProfile ? "От ответов зависит список документов" : "Загрузите каждый документ в свой пункт. Если ошибётесь пунктом — мы перенесём сами"} />
      <KeyCard id={id} resumeCode={fresh?.resumeCode} />
      {app.status === "ACCEPTED" && (
        <Card className="mb-4 flex items-start gap-3 bg-success-soft p-4 text-success-soft-foreground"><PartyPopper className="size-5 shrink-0" /><div><p className="font-semibold">Комплект принят</p><p className="text-sm">Кадровая служба свяжется с вами{app.decisionComment ? `: ${app.decisionComment}` : ""}</p></div></Card>
      )}
      {app.status === "RETURNED" && (
        <Card className="mb-4 flex items-start gap-3 bg-danger-soft p-4 text-danger-soft-foreground"><Undo2 className="size-5 shrink-0" /><div><p className="font-semibold">Заявку вернули на доработку</p>{app.decisionComment && <p className="text-sm">{app.decisionComment}</p>}</div></Card>
      )}
      {openRequests.length > 0 && (
        <Card className="mb-4 bg-warning-soft p-4 text-warning-soft-foreground sm:p-5">
          <p className="flex items-center gap-2 font-semibold"><MessageSquareWarning className="size-5" />Оператор просит</p>
          <ul className="mt-2 flex list-disc flex-col gap-1 pl-6 text-sm">
            {openRequests.map((r) => <li key={r.id}>{r.type ? <b className="font-medium">{titleOf(r.type)}: </b> : null}{r.reason}</li>)}
          </ul>
        </Card>
      )}
      {!hasProfile || editProfile ? (
        <Card className="p-4 sm:p-6"><ProfileForm applicationId={id} initial={app.profile} onDone={() => setEditProfile(false)} /></Card>
      ) : (
        <>
          <DocumentChecklist app={app} titleOf={titleOf} />
          {!decided && (
            <Card className="mt-4 p-4 sm:p-5">
              {missing.length ? (
                <p className="text-sm"><span className="font-semibold">Не хватает:</span> {missing.map(titleOf).join(", ")}</p>
              ) : (
                <p className="text-sm font-semibold text-success">Комплект полный</p>
              )}
              <p className="mt-1 text-sm text-muted-foreground">{app.status === "DRAFT" ? "Когда всё загрузите — отправьте комплект оператору" : "Комплект у оператора — новые файлы он увидит сразу"}</p>
              {app.status === "DRAFT" && <div className="mt-4"><SubmitButton applicationId={id} ready={!missing.length} titleOf={titleOf} /></div>}
            </Card>
          )}
          {!decided && <div className="mt-4 flex justify-center"><Button variant="quiet" onClick={() => setEditProfile(true)}><Pencil />Изменить ответы анкеты</Button></div>}
        </>
      )}
      <p className="mt-8 text-center text-xs text-muted-foreground">Другая заявка? <Link to={routes.apply} className="underline">Начать заново</Link></p>
    </>
  );
};
