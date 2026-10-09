import { useEffect, useState } from "react";
import { NavLink, Outlet, Navigate, useLocation, Link } from "react-router";
import { motion } from "motion/react";
import { QrCode, History, CalendarDays, UserRound } from "lucide-react";
import { loadKey, cn, type StoredKey } from "@/shared/lib";
import { Logo, OfflineBanner, Skeleton, PreferencesButton, InstallButton, HeaderBar, TrackIndicator, useTrackIndicator } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { startWorkerSync, PHONE_SLOT } from "@/shared/api";
import { pageIn } from "@/shared/config/motion";

const TABS = [
  { to: routes.worker, label: "Пропуск", icon: QrCode, end: true },
  { to: routes.workerHistory, label: "История", icon: History },
  { to: routes.workerShifts, label: "Смены", icon: CalendarDays },
  { to: routes.workerProfile, label: "Профиль", icon: UserRound },
];

export type WorkerCtx = { key: StoredKey };

/** Оболочка PWA рабочего: без ключа устройства — на активацию. */
export const WorkerShell = () => {
  const [key, setKey] = useState<StoredKey | null | undefined>(undefined);
  const loc = useLocation();
  const ind = useTrackIndicator(loc.pathname + String(key === undefined));
  useEffect(() => { loadKey(PHONE_SLOT).then((k) => setKey(k ?? null)).catch(() => setKey(null)); }, [loc.pathname]);
  // Свои данные с сервера (device-токен): карточка, проходы, смены, поток событий. Без сети — кэш карточки.
  const keyId = key?.deviceId;
  useEffect(() => (keyId ? startWorkerSync() : undefined), [keyId]);
  if (key === null) return <Navigate to={routes.workerActivate + loc.search} replace />;
  return (
    <div className="relative isolate min-h-svh text-foreground">
      {/* Та же шапка-пилюля, что в кабинете руководителя: знак слева, действия справа, одинаковые отступы. */}
      <HeaderBar inner="max-w-lg">
        <Link to={routes.home} className="min-w-0 shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="сотрудник" /></Link>
        <div className="ml-auto flex shrink-0 items-center gap-1.5"><InstallButton className="hidden sm:inline-flex" /><PreferencesButton /></div>
      </HeaderBar>
      <OfflineBanner text="Нет сети. QR-пропуск работает офлайн" />
      <div className="mx-auto w-full max-w-lg px-4 pb-nav pt-6 sm:px-6 sm:pt-8">
        {key === undefined ? (
          <div className="flex flex-col gap-4"><Skeleton className="h-16" /><Skeleton className="h-96 rounded-xl" /><Skeleton className="h-20" /></div>
        ) : (
          <motion.main key={loc.pathname} {...pageIn}>
            <Outlet context={{ key } satisfies WorkerCtx} />
          </motion.main>
        )}
      </div>
      {/* Нижняя навигация — как у кабинета руководителя на телефоне */}
      <nav className="fixed inset-x-0 bottom-0 z-nav px-3 pb-safe" aria-label="Разделы">
        <div ref={ind.ref} className="relative mx-auto mb-3 flex max-w-md items-stretch gap-0.5 rounded-xl bg-card/90 p-1 shadow-float backdrop-blur-xl">
          <TrackIndicator ind={ind} className="inset-y-1 rounded-lg bg-accent" />
          {TABS.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => cn("relative flex min-h-control-lg min-w-0 flex-1 flex-col items-center justify-center gap-0.5 rounded-md text-xs font-medium outline-none focus-visible:ring-2 focus-visible:ring-ring", isActive ? "text-accent-foreground" : "text-muted-foreground")}>
              <Icon className="relative z-raised size-5" /><span className="relative z-raised">{label}</span>
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  );
};
