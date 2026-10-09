import { useState } from "react";
import { Link, Outlet, useLocation } from "react-router";
import { motion } from "motion/react";
import { ArrowRight, LogIn, LogOut, ShieldCheck } from "lucide-react";
import { useSession, useOperatorAuth, can, roleLabel, type Perm } from "@/entities/session";
import { Avatar, Button, Dialog, EmptyState, HeaderBar, Logo, NavTrack, OfflineBanner, PreferencesButton, Spinner, Status } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { fadeUp, pageIn, stagger } from "@/shared/config/motion";
import { ModelStatusBadge } from "./ModelStatusBadge";

const NAV: { to: string; label: string; perm: Perm; end?: boolean }[] = [
  { to: routes.operator, label: "Очередь", perm: "queue", end: true },
  { to: routes.operatorExpiring, label: "Истекающие", perm: "reports" },
  { to: routes.operatorMetrics, label: "Метрики", perm: "metrics" },
];

/** Вход сотрудника: единый вход Keycloak (PKCE).  */
const SignIn = ({ onSignIn }: { onSignIn: () => void }) => (
  <div className="relative isolate flex min-h-svh flex-col">
    <HeaderBar inner="max-w-6xl">
      <Link to={routes.home} className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="рабочее место" /></Link>
      <div className="ml-auto flex shrink-0 items-center gap-1.5"><PreferencesButton /></div>
    </HeaderBar>
    <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-4 pb-16 pt-8 sm:px-6">
      <motion.div variants={stagger(0.07)} initial="hidden" animate="show" className="flex flex-col items-center text-center">
        <motion.p variants={fadeUp} className="text-sm font-medium text-brand">Операторы, старшие и аудиторы</motion.p>
        <motion.h1 variants={fadeUp} className="mt-5 text-balance font-display text-4xl font-semibold leading-none tracking-hero sm:text-5xl">Вход в рабочее место</motion.h1>
        <motion.p variants={fadeUp} className="mt-3 text-pretty text-base text-foreground/70">Единая корпоративная учётная запись. Права проверяет сервер</motion.p>
      </motion.div>
      <motion.section variants={fadeUp} initial="hidden" animate="show" className="mt-6 flex flex-col gap-5 overflow-hidden rounded-3xl bg-card/85 p-5 shadow-card backdrop-blur-xl sm:p-8">
        <Button variant="brand" size="lg" block className="h-14 rounded-lg" onClick={onSignIn}><LogIn />Войти<ArrowRight /></Button>
        <p className="text-center text-xs text-muted-foreground">Нет доступа — обратитесь к администратору стенда</p>
      </motion.section>
    </main>
  </div>
);

/** FE-OP-01: зона оператора — SSO (PKCE), разделы по ролям, статус модели, тема. */
export const OperatorShell = () => {
  useOperatorAuth();
  const s = useSession();
  const loc = useLocation();
  const [account, setAccount] = useState(false);
  if (!s.ready) return <div className="grid min-h-svh place-items-center"><Spinner className="size-8" /></div>;
  if (!s.user) return <SignIn onSignIn={() => void s.signIn()} />;
  const items = NAV.filter((n) => can(s.roles, n.perm));
  return (
    <div className="relative isolate min-h-svh text-foreground">
      <HeaderBar>
        <Link to={routes.operator} className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="оператор" /></Link>
        <NavTrack items={items.map(({ to, label, end }) => ({ to, label, end }))} className="ml-auto hidden md:block xl:ml-6" />
        <div className="ml-auto flex shrink-0 items-center gap-1.5">
          <ModelStatusBadge className="hidden lg:inline-flex" />
          <PreferencesButton className="hidden sm:flex" />
          <Button variant="quiet" className="h-12 rounded-md" onClick={() => setAccount(true)} aria-label={`Пользователь: ${s.name}`}><Avatar name={s.name} className="size-7" /><span className="hidden xl:inline">{roleLabel(s.role)}</span></Button>
        </div>
      </HeaderBar>
      <OfflineBanner />
      <nav className="mx-auto mt-3 flex max-w-7xl px-4 md:hidden" aria-label="Разделы"><NavTrack items={items.map(({ to, label, end }) => ({ to, label, end }))} /></nav>
      <motion.main key={loc.pathname} {...pageIn} className="mx-auto w-full max-w-7xl px-4 pb-16 pt-6 sm:px-6 sm:pt-8">
        {s.roles.length ? <Outlet /> : <EmptyState icon={<ShieldCheck />} title="Нет роли" text="Учётной записи не назначена роль оператора — обратитесь к администратору" className="py-24" />}
      </motion.main>
      <Dialog open={account} onClose={() => setAccount(false)} title="Учётная запись">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3 rounded-lg bg-muted p-4">
            <Avatar name={s.name} className="size-11" />
            <span className="flex min-w-0 flex-1 flex-col"><span className="truncate font-medium">{s.name}</span><span className="truncate text-sm text-muted-foreground">{s.login}</span></span>
            <Status tone="success">{roleLabel(s.role)}</Status>
          </div>
          <ModelStatusBadge className="self-start" />
          <Button variant="danger-soft" onClick={() => void s.signOut()}><LogOut />Выйти</Button>
        </div>
      </Dialog>
    </div>
  );
};
