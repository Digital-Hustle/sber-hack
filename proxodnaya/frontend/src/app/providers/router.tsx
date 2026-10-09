import { useRef } from "react";
import { createBrowserRouter, Navigate, useLocation, useOutlet } from "react-router";
import { AnimatePresence, motion, useIsPresent } from "motion/react";
import { Aurora } from "@/shared/ui";
import { tween } from "@/shared/config/motion";
import { HomePage } from "@/pages/home";
import { AdminShell } from "@/widgets/admin-shell";
import { WorkerShell } from "@/widgets/worker-shell";

// Каждая страница — отдельный чанк (recharts, zxing, ogl и т. п. не грузятся на главной).
// react-router дожидается чанка до смены экрана, поэтому переход без мигания пустого экрана.
const kiosk = () => import("@/pages/kiosk");
const worker = () => import("@/pages/worker");
const login = () => import("@/pages/login");
const dashboard = () => import("@/pages/admin-dashboard");
const people = () => import("@/pages/admin-people");
const journal = () => import("@/pages/admin-journal");
const shifts = () => import("@/pages/admin-shifts");
const analytics = () => import("@/pages/admin-analytics");
const assistant = () => import("@/pages/admin-assistant");
const settings = () => import("@/pages/admin-settings");
const objects = () => import("@/pages/admin-objects");
const terminals = () => import("@/pages/admin-terminals");
const access = () => import("@/pages/admin-access");

/** Фоновая предзагрузка всех страниц после первого экрана: дальнейшие переходы мгновенные. */
export const prefetchRoutes = () => {
  const run = () => [kiosk, worker, dashboard, people, journal, shifts, analytics, assistant, settings, objects, terminals, access].forEach((f) => { f().catch(() => {}); });
  if ("requestIdleCallback" in window) requestIdleCallback(run); else setTimeout(run, 1);
};

/** Уходящий раздел дорисовывает свой последний экран, пока гаснет (иначе он мгновенно подменился бы новым). */
const Frozen = () => {
  const outlet = useOutlet();
  const present = useIsPresent();
  const last = useRef(outlet);
  if (present) last.current = outlet;
  return last.current;
};

/**
 * Общий корень: одно живое сияние на все разделы (не пересоздаётся при переходе — фон не мигает)
 * и мягкая смена раздела: главная ↔ киоск ↔ пропуск ↔ кабинет — затухание и проявление.
 * Внутри раздела страницы меняются своим pageIn (оболочка и шапка остаются на месте).
 */
const RootLayout = () => {
  const { pathname } = useLocation();
  const section = pathname.split("/")[1] || "home";
  return (
    <div className="relative isolate min-h-svh text-foreground">
      <Aurora fixed />
      <AnimatePresence mode="wait" initial={false} onExitComplete={() => scrollTo(0, 0)}>
        <motion.div key={section} initial={{ opacity: 0 }} animate={{ opacity: 1, transition: tween.base }} exit={{ opacity: 0, transition: tween.exit }}>
          <Frozen />
        </motion.div>
      </AnimatePresence>
    </div>
  );
};

// HashRouter: GitHub Pages и любой статический хостинг без настройки fallback.
export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
    { path: "/", element: <HomePage /> },
    { path: "/kiosk", lazy: async () => ({ Component: (await kiosk()).KioskPage }) },
    { path: "/worker/activate", lazy: async () => ({ Component: (await worker()).WorkerActivatePage }) },
    {
      path: "/worker", element: <WorkerShell />,
      children: [
        { index: true, lazy: async () => ({ Component: (await worker()).WorkerPassPage }) },
        { path: "history", lazy: async () => ({ Component: (await worker()).WorkerHistoryPage }) },
        { path: "shifts", lazy: async () => ({ Component: (await worker()).WorkerShiftsPage }) },
        { path: "profile", lazy: async () => ({ Component: (await worker()).WorkerProfilePage }) },
        { path: "face", lazy: async () => ({ Component: (await worker()).WorkerFacePage }) },
      ],
    },
    { path: "/login", lazy: async () => ({ Component: (await login()).LoginPage }) },
    {
      path: "/admin", element: <AdminShell />,
      children: [
        { index: true, lazy: async () => ({ Component: (await dashboard()).DashboardPage }) },
        { path: "people", lazy: async () => ({ Component: (await people()).PeoplePage }) },
        { path: "people/new", lazy: async () => ({ Component: (await people()).NewPersonPage }) },
        { path: "people/:id", lazy: async () => ({ Component: (await people()).PersonPage }) },
        { path: "journal", lazy: async () => ({ Component: (await journal()).JournalPage }) },
        { path: "shifts", lazy: async () => ({ Component: (await shifts()).ShiftsPage }) },
        { path: "analytics", lazy: async () => ({ Component: (await analytics()).AnalyticsPage }) },
        { path: "assistant", lazy: async () => ({ Component: (await assistant()).AssistantPage }) },
        { path: "settings", lazy: async () => ({ Component: (await settings()).SettingsPage }) },
        { path: "objects", lazy: async () => ({ Component: (await objects()).ObjectsPage }) },
        { path: "terminals", lazy: async () => ({ Component: (await terminals()).TerminalsPage }) },
        { path: "access", lazy: async () => ({ Component: (await access()).AccessPage }) },
      ],
    },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);
