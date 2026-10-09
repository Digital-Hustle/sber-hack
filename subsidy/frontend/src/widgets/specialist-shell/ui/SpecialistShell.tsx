import { Link, Outlet, useLocation } from "react-router";
import { motion } from "motion/react";
import { Logo, HeaderBar, NavTrack, PreferencesButton, OfflineBanner } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { pageIn } from "@/shared/config/motion";

const NAV = [
  { to: routes.packages, label: "Пакеты" },
];

/** Оболочка рабочего места: шапка-пилюля как в кабинете «Проходной», плавная смена страниц. */
export const SpecialistShell = () => {
  const loc = useLocation();
  return (
    <div className="relative isolate min-h-svh text-foreground">
      <HeaderBar>
        <Link to={routes.packages} className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="специалист" /></Link>
        <NavTrack items={NAV} className="ml-auto hidden lg:block xl:ml-6" />
        <div className="ml-auto flex shrink-0 items-center gap-1.5">
          <PreferencesButton className="hidden lg:flex" />
        </div>
      </HeaderBar>
      <OfflineBanner />
      <motion.main key={loc.pathname} {...pageIn} className="mx-auto w-full max-w-7xl px-4 pb-16 pt-6 sm:px-6 sm:pt-8 lg:px-8">
        <Outlet />
      </motion.main>
    </div>
  );
};
