import { Link } from "react-router";
import { motion } from "motion/react";
import { ExternalLink, MonitorSmartphone } from "lucide-react";
import { Button, StyledQr } from "@/shared/ui";
import { routes, absoluteUrl } from "@/shared/const/router";
import { fadeUp, popIn, stagger } from "@/shared/config/motion";

const STEPS = ["Откройте админку → «Терминалы»", "Введите код или отсканируйте QR", "Выберите проходную и логику работы"];

/** Несопряжённый терминал: никого не пропускает, показывает код для привязки в админке (ADR-038). */
export const PairingScreen = ({ code }: { code: string }) => {
  const path = `${routes.adminTerminals}?code=${code}`;
  return (
    <div className="relative isolate flex min-h-0 flex-1 flex-col">
      <motion.div variants={stagger(0.06)} initial="hidden" animate="show" className="relative flex min-h-0 flex-1 flex-col items-center justify-center gap-6 overflow-y-auto p-5 text-center text-white sm:gap-8 sm:p-8">
        <motion.span variants={popIn} className="flex size-14 items-center justify-center rounded-full bg-white/10 backdrop-blur-md"><MonitorSmartphone className="size-7" /></motion.span>
        <motion.div variants={fadeUp} className="flex flex-col gap-2">
          <h1 className="text-balance font-display text-3xl font-semibold tracking-display sm:text-4xl">Терминал не подключён</h1>
          <p className="text-balance text-base text-white/70 sm:text-lg">До привязки к проходной терминал никого не пропускает</p>
        </motion.div>
        <motion.div variants={fadeUp} className="flex flex-col items-center gap-6 sm:flex-row sm:gap-10">
          <div className="rounded-xl bg-white p-3 shadow-pop"><StyledQr value={absoluteUrl(path)} className="size-40 sm:size-48" label="QR для привязки терминала" /></div>
          <div className="flex flex-col items-center gap-4 sm:items-start">
            <span className="text-sm text-white/60">Код сопряжения</span>
            <motion.div variants={stagger(0.05)} className="flex gap-1.5 sm:gap-2" aria-label={`Код ${code.split("").join(" ")}`}>
              {code.split("").map((ch, i) => (
                <motion.span key={i} variants={popIn} className="flex h-14 w-11 items-center justify-center rounded-md bg-white/10 font-display text-3xl font-semibold tabular-nums ring-1 ring-white/15 backdrop-blur-md sm:h-16 sm:w-12">{ch}</motion.span>
              ))}
            </motion.div>
            <ol className="flex flex-col gap-1.5 text-left text-sm text-white/70">
              {STEPS.map((s, i) => <li key={s} className="flex gap-2"><span className="tabular-nums text-white/40">{i + 1}.</span>{s}</li>)}
            </ol>
          </div>
        </motion.div>
        <motion.div variants={fadeUp} className="flex flex-col items-center gap-3">
          <Link to={path} target="_blank" tabIndex={-1}><Button variant="secondary" size="lg"><ExternalLink />Привязать в админке</Button></Link>
          <p className="text-xs text-white/50">Сервисная панель: удерживайте логотип 1 секунду</p>
        </motion.div>
      </motion.div>
    </div>
  );
};
