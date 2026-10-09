import { useState } from "react";
import { motion } from "motion/react";
import { FileCheck2, Smartphone, Timer } from "lucide-react";
import { StartForm, ResumeForm } from "@/features/start-application";
import { Card, PageHeader, Segmented } from "@/shared/ui";
import { fadeUp, stagger } from "@/shared/config/motion";

const STEPS = [
  { icon: FileCheck2, text: "Ответьте на 2–3 вопроса — покажем, какие документы нужны именно вам" },
  { icon: Smartphone, text: "Сфотографируйте или загрузите сканы — вид документа определим сами" },
  { icon: Timer, text: "Плохой снимок увидите сразу, а не через неделю — с подсказкой, что исправить" },
];

/** /apply — начало: новая заявка или возврат по коду. */
export const ApplyStartPage = () => {
  const [tab, setTab] = useState<"new" | "resume">("new");
  return (
    <>
      <PageHeader kicker="Оформление на работу" title="Документы для оформления" sub="Соберите комплект с телефона за несколько минут — без регистрации и пароля" />
      <motion.ul variants={stagger()} initial="hidden" animate="show" className="mb-6 flex flex-col gap-2">
        {STEPS.map(({ icon: Icon, text }) => (
          <motion.li key={text} variants={fadeUp} className="flex items-start gap-3 rounded-lg bg-card/70 p-3 text-sm">
            <span className="grid size-9 shrink-0 place-items-center rounded-md bg-accent text-accent-foreground"><Icon className="size-5" /></span>
            <span className="pt-2">{text}</span>
          </motion.li>
        ))}
      </motion.ul>
      <Card className="p-4 sm:p-6">
        <Segmented block size="lg" className="mb-5" label="Заявка" value={tab} onChange={setTab}
          options={[{ value: "new", label: "Новая заявка" }, { value: "resume", label: "У меня есть код" }]} />
        {tab === "new" ? <StartForm /> : <ResumeForm />}
      </Card>
    </>
  );
};
