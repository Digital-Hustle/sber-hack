import { useState } from "react";
import { Link } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, ArrowRight, ShieldCheck, Smartphone, X } from "lucide-react";
import { SignInFailure, forgetLogin, recentLogins, signInWithCode, type SignInError } from "@/entities/session";
import { Avatar, Button, CodeInput, Field, HeaderBar, Input, Logo, PreferencesButton, type CodeState } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { fadeUp, popIn, press, spring, stagger, tween } from "@/shared/config/motion";

const LOGIN = /^[a-z][a-z0-9._-]{2,31}$/;
const swap = { initial: { opacity: 0, x: 24 }, animate: { opacity: 1, x: 0 }, exit: { opacity: 0, x: -24, transition: tween.exit }, transition: { ...spring.soft, opacity: tween.base } };
const REASON: Record<SignInError, string> = {
  BAD_CODE: "Код не подошёл. Проверьте логин и введите свежий код — он меняется каждые 30 секунд",
  LOCKED: "Вход временно заблокирован после неудачных попыток или отключён старшим. Подождите минуту или обратитесь к старшему",
  NOT_CONFIGURED: "Для этой учётной записи код ещё не выдан — попросите старшего оператора",
  OFFLINE: "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз",
};

/**
 * FE-UI-03: вход сотрудника без пароля и без писем — логин и 6 цифр из приложения-аутентификатора
 * (Google Authenticator, Яндекс Ключ и т. п.). Код выдаёт старший оператор в разделе «Сотрудники» (QR).
 * Форма — своя, в стиле песочницы; проверяет Keycloak, токены живут в памяти вкладки.
 */
export const SignIn = () => {
  const [recent, setRecent] = useState(recentLogins);
  const [login, setLogin] = useState(recent[0] ?? "");
  const [step, setStep] = useState<"login" | "code">(recent[0] ? "code" : "login");
  const [code, setCode] = useState("");
  const [state, setState] = useState<CodeState>("idle");
  const [err, setErr] = useState<string | null>(null);
  const valid = LOGIN.test(login.trim().toLowerCase());

  const toCode = (l = login) => { setLogin(l.trim().toLowerCase()); setStep("code"); setErr(null); setCode(""); setState("idle"); };
  const verify = async (v: string) => {
    setState("busy"); setErr(null);
    try {
      await new Promise((r) => setTimeout(r, 350)); // волна по ячейкам успевает показать «проверяем»
      await signInWithCode(login, v);
      setState("success");
    } catch (e) {
      setErr(REASON[e instanceof SignInFailure ? e.reason : "BAD_CODE"]); setState("error");
      setTimeout(() => { setCode(""); setState("idle"); }, 650);
    }
  };

  return (
    <div className="relative isolate flex min-h-svh flex-col">
      <HeaderBar inner="max-w-6xl">
        <Link to={routes.home} className="shrink-0 rounded-md px-1.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"><Logo sub="рабочее место" /></Link>
        <div className="ml-auto flex shrink-0 items-center gap-1.5"><PreferencesButton /></div>
      </HeaderBar>

      <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-4 pb-16 pt-8 sm:px-6">
        <motion.div variants={stagger(0.07)} initial="hidden" animate="show" className="flex flex-col items-center text-center">
          <motion.p variants={fadeUp} className="text-sm font-medium text-brand">Операторы, старшие и аудиторы</motion.p>
          <motion.h1 variants={fadeUp} className="mt-5 text-balance font-display text-4xl font-semibold leading-none tracking-hero sm:text-5xl">Вход в рабочее место</motion.h1>
          <motion.p variants={fadeUp} className="mt-3 text-pretty text-base text-foreground/70">Без пароля: логин и код из приложения-аутентификатора</motion.p>
        </motion.div>

        <motion.section variants={fadeUp} initial="hidden" animate="show" className="mt-6 overflow-hidden rounded-3xl bg-card/85 p-5 shadow-card backdrop-blur-xl sm:p-8">
          <AnimatePresence mode="wait" initial={false}>
            {step === "login" ? (
              <motion.form key="login" {...swap} className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); if (valid) toCode(); }}>
                <Field label="Логин" hint="Его выдал старший оператор вместе с QR-кодом">
                  <Input autoComplete="username" autoCapitalize="none" spellCheck={false} autoFocus value={login}
                    onChange={(e) => setLogin(e.target.value)} placeholder="например, ivanov" />
                </Field>
                <Button type="submit" variant="brand" size="lg" block className="h-14 rounded-lg" disabled={!valid}>Далее<ArrowRight /></Button>
                {recent.length > 0 && (
                  <div className="flex flex-col gap-1 border-t border-border pt-4">
                    <span className="mb-1 text-xs font-medium text-muted-foreground">Входили на этом устройстве</span>
                    <AnimatePresence initial={false}>
                      {recent.map((l) => (
                        <motion.div key={l} layout="position" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} transition={{ ...spring.soft, opacity: tween.fast }} className="flex items-center gap-1">
                          <motion.button type="button" {...press} onClick={() => toCode(l)}
                            className="flex min-w-0 flex-1 items-center gap-3 rounded-lg px-2 py-2 text-left outline-none transition-colors duration-fast hover:bg-surface focus-visible:ring-2 focus-visible:ring-ring">
                            <Avatar name={l} className="size-8 text-xs" />
                            <span className="min-w-0 flex-1 truncate text-sm font-medium">{l}</span>
                            <ArrowRight className="size-4 text-subtle-foreground" />
                          </motion.button>
                          <Button type="button" size="icon-sm" variant="quiet" aria-label={`Забыть ${l}`} onClick={() => { forgetLogin(l); setRecent(recentLogins()); }}><X /></Button>
                        </motion.div>
                      ))}
                    </AnimatePresence>
                  </div>
                )}
              </motion.form>
            ) : (
              <motion.div key="code" {...swap} className="flex flex-col gap-5">
                <div className="flex items-center gap-3">
                  <motion.span variants={popIn} initial="hidden" animate="show" className="flex size-10 shrink-0 items-center justify-center rounded-md bg-accent text-accent-foreground"><Smartphone className="size-5" /></motion.span>
                  <p className="min-w-0 text-pretty text-sm text-muted-foreground">Откройте приложение-аутентификатор и введите код для <span className="font-medium text-foreground">{login}</span></p>
                </div>
                <CodeInput length={6} value={code} onChange={(v) => { setCode(v); setErr(null); }} onComplete={verify} state={state} autoFocus label="Код из приложения" />
                <AnimatePresence mode="wait" initial={false}>
                  <motion.p key={state === "success" ? "ok" : err ?? "hint"} initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.fast }}
                    className={state === "success" ? "flex items-center gap-1.5 text-sm font-medium text-success" : err ? "text-pretty text-sm text-danger" : "text-sm text-muted-foreground"}>
                    {state === "success" ? <><ShieldCheck className="size-4" />Готово, открываем рабочее место</> : err ?? (state === "busy" ? "Проверяем код…" : "Введите 6 цифр — проверим сами")}
                  </motion.p>
                </AnimatePresence>
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-4">
                  <Button variant="quiet" size="sm" onClick={() => { setStep("login"); setErr(null); setCode(""); setState("idle"); }}><ArrowLeft />Другой логин</Button>
                  <span className="text-xs text-muted-foreground">Потеряли телефон — к старшему</span>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.section>
      </main>
    </div>
  );
};
