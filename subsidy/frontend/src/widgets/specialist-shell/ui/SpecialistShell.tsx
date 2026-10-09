import { useState } from "react";
import { Link, Outlet, useLocation } from "react-router";
import { motion } from "motion/react";
import { LogIn, LogOut, ShieldCheck } from "lucide-react";
import { useSession, useSpecialistAuth, can, roleLabel, type Perm } from "@/entities/session";
import { Avatar, Button, Card, Dialog, EmptyState, HeaderBar, Logo, NavTrack, OfflineBanner, PreferencesButton, Spinner, Status } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { pageIn } from "@/shared/config/motion";
import { ModelStatusBadge } from "./ModelStatusBadge";

const NAV: { to: string; label: string; perm: Perm }[] = [
  { to: routes.packages, label: "Пакеты", perm: "stats" },
  { to: routes.expiring, label: "Истекающие", perm: "stats" },
  { to: routes.stats, label: "Частые ошибки", perm: "stats" },
  { to: routes.programs, label: "Программы", perm: "stats" },
];

const SignIn = ({ onSignIn }: { onSignIn: () => void }) => (
  <div className="mx-auto flex min-h-svh max-w-md flex-col justify-center px-4">
    <Card className="p-6 sm:p-8">
      <Logo sub="специалист" />
      <h1 className="mt-6 font-display text-2xl font-semibold tracking-display">Вход для специалистов</h1>
      <p className="mt-2 text-sm text-muted-foreground">Единый вход через корпоративную учётную запись (Keycloak). Права проверяет сервер.</p>
      <Button size="lg" block className="mt-6" onClick={onSignIn}><LogIn />Войти</Button>
    </Card>
  </div>
);

/** Рабочее место: SSO (PKCE), разделы по ролям, статус модели, тема. */
export const SpecialistShell = () => {
  useSpecialistAuth();
  const s = useSession();
  const loc = useLocation();
  const [account, setAccount] = useState(false);
  if (!s.ready) return <div className="grid min-h-svh place-items-center"><Spinner className="size-8" /></div>;
  if (!s.user) return <SignIn onSignIn={() => void s.signIn()} />;
  const items = NAV.filter((n) => can(s.roles, n.perm)).map(({ to, label }) => ({ to, label }));
  return (
    <div className="relative isolate min-h-svh text-foreground">
      <HeaderBar>
        <Link to={routes.packages} className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="специалист" /></Link>
        <NavTrack items={items} className="ml-auto hidden lg:block xl:ml-6" />
        <div className="ml-auto flex shrink-0 items-center gap-1.5">
          <ModelStatusBadge className="hidden xl:inline-flex" />
          <PreferencesButton className="hidden sm:flex" />
          <Button variant="quiet" className="h-12 rounded-md" onClick={() => setAccount(true)} aria-label={`Пользователь: ${s.name}`}><Avatar name={s.name} className="size-7" /><span className="hidden xl:inline">{roleLabel(s.role)}</span></Button>
        </div>
      </HeaderBar>
      <OfflineBanner />
      <nav className="mx-auto mt-3 flex max-w-7xl px-4 lg:hidden" aria-label="Разделы"><NavTrack items={items} /></nav>
      <motion.main key={loc.pathname} {...pageIn} className="mx-auto w-full max-w-screen-2xl px-4 pb-16 pt-6 sm:px-6 lg:px-8">
        {s.roles.length ? <Outlet /> : <EmptyState icon={<ShieldCheck />} title="Нет роли" text="Учётной записи не назначена роль специалиста — обратитесь к администратору" className="py-24" />}
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
