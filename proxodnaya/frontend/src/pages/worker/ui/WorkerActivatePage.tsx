import { useNavigate, useSearchParams, Link } from "react-router";
import { motion } from "motion/react";
import { Logo, PreferencesButton, HeaderBar } from "@/shared/ui";
import { ActivateForm } from "@/features/activate-device";
import { routes } from "@/shared/const/router";
import { fadeUp } from "@/shared/config/motion";

export const WorkerActivatePage = () => {
  const [sp] = useSearchParams();
  const nav = useNavigate();
  return (
    <div className="relative isolate flex min-h-svh flex-col overflow-x-clip">
      <HeaderBar inner="max-w-lg">
        <Link to={routes.home} className="min-w-0 shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="сотрудник" /></Link>
        <div className="ml-auto flex shrink-0 items-center gap-1.5"><PreferencesButton /></div>
      </HeaderBar>
      <div className="relative mx-auto flex w-full max-w-lg flex-1 flex-col px-4 sm:px-6">
        <motion.div variants={fadeUp} initial="hidden" animate="show" className="my-auto rounded-2xl bg-card px-5 py-8 shadow-pop sm:px-8">
          <ActivateForm initialCode={sp.get("c") ?? ""} onDone={() => nav(routes.worker, { replace: true })} />
        </motion.div>
        <p className="mx-auto max-w-xs py-6 text-center text-xs text-muted-foreground">Пароль не требуется: смартфон получает собственный криптографический ключ, который нельзя перенести на другое устройство.</p>
      </div>
    </div>
  );
};
