import { useEffect, useState } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, ArrowRight, FlaskConical, MailCheck, ShieldCheck } from "lucide-react";
import { SignInFailure, authMode, requestLoginCode, roleLabel, signInWithCode, testCode, type SignInError, type TestUser } from "@/entities/session";
import { errorText } from "@/shared/api";
import { Avatar, Button, CodeInput, Field, HeaderBar, Input, Logo, PreferencesButton, Spinner, Status, type CodeState } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { env } from "@/shared/config/env";
import { fadeUp, popIn, press, spring, stagger, tween } from "@/shared/config/motion";
import { plural } from "@/shared/lib";

const CODE_LEN = 6;
const TEST_CODE = "000000";
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
type Sent = { email: string; sentTo: string; expiresAt: number; resendAt: number };
const swap = { initial: { opacity: 0, x: 24 }, animate: { opacity: 1, x: 0 }, exit: { opacity: 0, x: -24, transition: tween.exit }, transition: { ...spring.soft, opacity: tween.base } };
const REASON: Record<SignInError, string> = {
  BAD_CODE: "Код не подошёл или истёк — запросите новый",
  LOCKED: "Вход временно заблокирован после неудачных попыток или запрещён старшим. Подождите минуту или обратитесь к старшему",
  NOT_CONFIGURED: "Сначала запросите код — он придёт на почту",
  OFFLINE: "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз",
};
const reason = (e: unknown) => (e instanceof SignInFailure ? REASON[e.reason] : errorText(e));

/** APP_MODE=dev: быстрый вход тестовыми сотрудниками. В prod-сборке env.testMode = false — компонент не рендерится и вырезается. */
const TestUsers = ({ onPick, busy }: { onPick: (u: TestUser) => void; busy: string | null }) => {
  const q = useQuery({ queryKey: ["auth-mode"], queryFn: authMode, staleTime: 60_000 });
  const users = q.data?.mode === "dev" ? q.data.testUsers : [];
  if (!users.length) return null;
  return (
    <div className="flex flex-col gap-1 border-t border-border pt-4">
      <span className="mb-1 text-xs font-medium text-muted-foreground">Быстрый вход на тестовом стенде</span>
      {users.map((u) => (
        <motion.button key={u.email} type="button" {...press} disabled={!!busy} onClick={() => onPick(u)}
          className="flex min-w-0 items-center gap-3 rounded-lg px-2 py-2 text-left outline-none transition-colors duration-fast hover:bg-surface focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60">
          <Avatar name={u.name} className="size-8 text-xs" />
          <span className="flex min-w-0 flex-1 flex-col"><span className="truncate text-sm font-medium">{u.name}</span><span className="truncate text-xs text-muted-foreground">{roleLabel(u.role)} · {u.email}</span></span>
          {busy === u.email ? <Spinner className="size-4" /> : <ArrowRight className="size-4 text-subtle-foreground" />}
        </motion.button>
      ))}
    </div>
  );
};

/**
 * FE-UI-04: вход сотрудника без пароля — рабочая почта и 6 цифр из письма (как в «Проходной»).
 * Код выдаёт review (RV-16), проверяет Keycloak; токены живут в памяти вкладки. ADR-021.
 */
export const SignIn = () => {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState<Sent | null>(null);
  const [code, setCode] = useState("");
  const [state, setState] = useState<CodeState>("idle");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [quick, setQuick] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { if (!sent) return; const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, [sent]);
  const resendIn = sent ? Math.max(0, Math.ceil((sent.resendAt - now) / 1000)) : 0;
  const valid = EMAIL.test(email.trim());

  const request = async (to = email) => {
    setBusy(true); setErr(null);
    try {
      const r = await requestLoginCode(to);
      const t = Date.now();
      setSent({ email: to.trim().toLowerCase(), sentTo: r.sentTo, expiresAt: t + r.expiresInSec * 1000, resendAt: t + r.resendInSec * 1000 });
      setCode(""); setState("idle");
    } catch (e) { setErr(reason(e)); } finally { setBusy(false); }
  };
  const verify = async (v: string) => {
    if (!sent) return;
    setState("busy"); setErr(null);
    try {
      await new Promise((r) => setTimeout(r, 350)); // волна по ячейкам успевает показать «проверяем»
      const c = env.testMode && v === TEST_CODE ? await testCode(sent.email) : v;
      await signInWithCode(sent.email, c);
      setState("success");
    } catch (e) {
      setErr(reason(e)); setState("error");
      setTimeout(() => { setCode(""); setState("idle"); }, 650);
    }
  };
  const quickSignIn = async (u: TestUser) => {
    setQuick(u.email); setErr(null);
    try { await signInWithCode(u.email, await testCode(u.email)); } catch (e) { setErr(reason(e)); } finally { setQuick(null); }
  };

  return (
    <div className="relative isolate flex min-h-svh flex-col">
      <HeaderBar inner="max-w-6xl">
        <Link to={routes.home} className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="рабочее место" /></Link>
        <div className="ml-auto flex shrink-0 items-center gap-1.5"><PreferencesButton /></div>
      </HeaderBar>

      <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-4 pb-16 pt-8 sm:px-6">
        <motion.div variants={stagger(0.07)} initial="hidden" animate="show" className="flex flex-col items-center text-center">
          {env.testMode && <motion.div variants={fadeUp} className="mb-4"><Status tone="warning"><FlaskConical className="size-3.5" />Тестовый стенд</Status></motion.div>}
          <motion.p variants={fadeUp} className="text-sm font-medium text-brand">Операторы, старшие и аудиторы</motion.p>
          <motion.h1 variants={fadeUp} className="mt-5 text-balance font-display text-4xl font-semibold leading-none tracking-hero sm:text-5xl">Вход в рабочее место</motion.h1>
          <motion.p variants={fadeUp} className="mt-3 text-pretty text-base text-foreground/70">Без пароля: пришлём одноразовый код на рабочую почту</motion.p>
        </motion.div>

        <motion.section variants={fadeUp} initial="hidden" animate="show" className="mt-6 overflow-hidden rounded-3xl bg-card/85 p-5 shadow-card backdrop-blur-xl sm:p-8">
          <AnimatePresence mode="wait" initial={false}>
            {!sent ? (
              <motion.form key="email" {...swap} className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); if (valid) void request(); }}>
                <Field label="Рабочая почта" error={err}>
                  <Input type="email" inputMode="email" autoComplete="username" autoCapitalize="none" spellCheck={false} autoFocus value={email}
                    onChange={(e) => { setEmail(e.target.value); setErr(null); }} placeholder="name@company.ru" />
                </Field>
                <Button type="submit" variant="brand" size="lg" block className="h-14 rounded-lg" disabled={!valid || busy}>{busy ? <Spinner /> : <>Получить код<ArrowRight /></>}</Button>
                {env.testMode && <TestUsers onPick={(u) => void quickSignIn(u)} busy={quick} />}
              </motion.form>
            ) : (
              <motion.div key="code" {...swap} className="flex flex-col gap-5">
                <div className="flex items-start gap-3">
                  <motion.span variants={popIn} initial="hidden" animate="show" className="flex size-10 shrink-0 items-center justify-center rounded-md bg-accent text-accent-foreground"><MailCheck className="size-5" /></motion.span>
                  <p className="min-w-0 text-pretty text-sm text-muted-foreground">Отправили код на <span className="font-medium text-foreground">{sent.sentTo}</span>. Он действует 10 минут. Если адреса нет в системе, письмо не придёт.{env.testMode && " На тестовом стенде подойдёт и 000000."}</p>
                </div>
                <CodeInput length={CODE_LEN} value={code} onChange={(v) => { setCode(v); setErr(null); }} onComplete={verify} state={state} autoFocus label="Код из письма" />
                <AnimatePresence mode="wait" initial={false}>
                  <motion.p key={state === "success" ? "ok" : err ?? "hint"} initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.fast }}
                    className={state === "success" ? "flex items-center gap-1.5 text-sm font-medium text-success" : err ? "text-pretty text-sm text-danger" : "text-sm text-muted-foreground"}>
                    {state === "success" ? <><ShieldCheck className="size-4" />Готово, открываем рабочее место</> : err ?? (state === "busy" ? "Проверяем код…" : `Введите ${CODE_LEN} цифр — проверим сами`)}
                  </motion.p>
                </AnimatePresence>
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-4">
                  <Button variant="quiet" size="sm" onClick={() => { setSent(null); setErr(null); setCode(""); setState("idle"); }}><ArrowLeft />Другая почта</Button>
                  <Button variant="quiet" size="sm" disabled={resendIn > 0 || busy} onClick={() => void request(sent.email)}>
                    {resendIn > 0 ? `Новый код через ${resendIn} ${plural(resendIn, "секунду", "секунды", "секунд")}` : "Отправить код ещё раз"}
                  </Button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.section>
      </main>
    </div>
  );
};
