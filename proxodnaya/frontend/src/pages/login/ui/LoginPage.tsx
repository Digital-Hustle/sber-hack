import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, ArrowRight, MailCheck, ShieldCheck } from "lucide-react";
import { api, errorText, ApiError, LOGIN_CODE_LEN } from "@/shared/api";
import { Button, CodeInput, Field, HeaderBar, Input, Logo, PreferencesButton, Spinner, type CodeState } from "@/shared/ui";
import { useSession } from "@/entities/session";
import { routes } from "@/shared/const/router";
import { fadeUp, popIn, spring, stagger, tween } from "@/shared/config/motion";
import { plural } from "@/shared/lib";

type Sent = { email: string; sentTo: string; expiresAt: number; resendAt: number };
const swap = { initial: { opacity: 0, x: 24 }, animate: { opacity: 1, x: 0 }, exit: { opacity: 0, x: -24, transition: tween.exit }, transition: { ...spring.soft, opacity: tween.base } };

/** Вход в кабинет по коду из письма — без пароля (ADR-046). Код вводится в ячейки, отправка — сама, когда введены все цифры. */
export const LoginPage = () => {
  const nav = useNavigate();
  const [sp] = useSearchParams();
  const next = sp.get("next") || routes.admin;
  const start = useSession((s) => s.start);
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState<Sent | null>(null);
  const [code, setCode] = useState("");
  const [state, setState] = useState<CodeState>("idle");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { if (!sent) return; const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, [sent]);
  const resendIn = sent ? Math.max(0, Math.ceil((sent.resendAt - now) / 1000)) : 0;

  const request = async (to = email) => {
    setBusy(true); setErr(null);
    try { const r = await api.requestLoginCode(to); setSent({ email: to.trim().toLowerCase(), ...r }); setCode(""); setState("idle"); }
    catch (e) { setErr(e instanceof ApiError || !(e instanceof Error) ? errorText(e) : e.message); } finally { setBusy(false); }
  };
  const verify = async (v: string) => {
    if (!sent) return;
    setState("busy"); setErr(null);
    try {
      const r = await api.verifyLoginCode(sent.email, v);
      setState("success");
      setTimeout(() => { start({ userId: r.user.id, role: r.user.role, token: r.token, expiresAt: r.expiresAt }); nav(next, { replace: true }); }, 900);
    } catch (e) {
      setErr(errorText(e)); setState("error");
      setTimeout(() => { setCode(""); setState("idle"); }, 650);
    }
  };

  return (
    <div className="relative isolate flex min-h-svh flex-col">
      <HeaderBar inner="max-w-6xl">
        <Link to={routes.home} className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="кабинет" /></Link>
        <div className="ml-auto flex shrink-0 items-center gap-1.5"><PreferencesButton /></div>
      </HeaderBar>

      <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-4 pb-16 pt-8 sm:px-6">
        <motion.div variants={stagger(0.07)} initial="hidden" animate="show" className="flex flex-col items-center text-center">
          <motion.p variants={fadeUp} className="text-sm font-medium text-brand">Руководители, охрана и инженеры</motion.p>
          <motion.h1 variants={fadeUp} className="mt-5 text-balance font-display text-4xl font-semibold leading-none tracking-hero sm:text-5xl">Вход в кабинет</motion.h1>
          <motion.p variants={fadeUp} className="mt-3 text-pretty text-base text-foreground/70">Без пароля: пришлём одноразовый код на рабочую почту</motion.p>
        </motion.div>


        <motion.section variants={fadeUp} initial="hidden" animate="show" className="mt-6 overflow-hidden rounded-3xl bg-card/85 p-5 shadow-card backdrop-blur-xl sm:p-8">
          <AnimatePresence mode="wait" initial={false}>
            {!sent ? (
              <motion.form key="email" {...swap} className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); request(); }}>
                <Field label="Рабочая почта" error={err}>
                  <Input type="email" inputMode="email" autoComplete="username" autoFocus value={email} onChange={(e) => { setEmail(e.target.value); setErr(null); }} placeholder="name@company.ru" />
                </Field>
                <Button type="submit" variant="brand" size="lg" block className="h-14 rounded-lg" disabled={!email.includes("@") || busy}>{busy ? <Spinner /> : <>Получить код<ArrowRight /></>}</Button>
              </motion.form>
            ) : (
              <motion.div key="code" {...swap} className="flex flex-col gap-5">
                <div className="flex items-start gap-3">
                  <motion.span variants={popIn} initial="hidden" animate="show" className="flex size-10 shrink-0 items-center justify-center rounded-md bg-accent text-accent-foreground"><MailCheck className="size-5" /></motion.span>
                  <p className="min-w-0 text-pretty text-sm text-muted-foreground">Отправили код на <span className="font-medium text-foreground">{sent.sentTo}</span>. Он действует 10 минут. Если адреса нет в системе, письмо не придёт.</p>
                </div>
                <CodeInput length={LOGIN_CODE_LEN} value={code} onChange={(v) => { setCode(v); setErr(null); }} onComplete={verify} state={state} autoFocus label="Код из письма" />
                <AnimatePresence mode="wait" initial={false}>
                  <motion.p key={state === "success" ? "ok" : err ?? "hint"} initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.fast }}
                    className={state === "success" ? "flex items-center gap-1.5 text-sm font-medium text-success" : err ? "text-sm text-danger" : "text-sm text-muted-foreground"}>
                    {state === "success" ? <><ShieldCheck className="size-4" />Готово, открываем кабинет</> : err ?? (state === "busy" ? "Проверяем код…" : `Введите ${LOGIN_CODE_LEN} цифр — проверим сами`)}
                  </motion.p>
                </AnimatePresence>
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-4">
                  <Button variant="quiet" size="sm" onClick={() => { setSent(null); setErr(null); setCode(""); setState("idle"); }}><ArrowLeft />Другая почта</Button>
                  <Button variant="quiet" size="sm" disabled={resendIn > 0 || busy} onClick={() => request(sent.email)}>{resendIn > 0 ? `Новый код через ${resendIn} ${plural(resendIn, "секунду", "секунды", "секунд")}` : "Отправить код ещё раз"}</Button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.section>
      </main>
    </div>
  );
};
