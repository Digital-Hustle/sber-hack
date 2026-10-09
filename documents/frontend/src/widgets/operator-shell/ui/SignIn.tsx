import { useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { RotateCcw } from "lucide-react";
import { SIGNIN_MESSAGE, completeEmbeddedSignIn, embeddedSignInUrl } from "@/entities/session";
import { Button, HeaderBar, Logo, PreferencesButton, Skeleton, Spinner } from "@/shared/ui";
import { routes } from "@/shared/const/router";
import { cn } from "@/shared/lib";
import { fadeUp, spring, stagger } from "@/shared/config/motion";

/** Заглушка на время загрузки формы: те же поле и кнопка, что у Keycloak. */
const FormSkeleton = () => (
  <div className="flex flex-col gap-3" aria-hidden>
    <Skeleton className="h-4 w-16" />
    <Skeleton className="h-13 w-full rounded-md" />
    <Skeleton className="mt-3 h-14 w-full rounded-lg" />
  </div>
);

/**
 * FE-UI-02: вход сотрудника прямо на странице. Форма Keycloak (логин → код) — во фрейме того же origin (/auth),
 * оформлена темой documents в «встроенном» виде: без своей шапки и фона, только поля и кнопка.
 * Высота фрейма следует за содержимым; после входа фрейм отдаёт адрес callback, токены остаются в памяти вкладки.
 */
export const SignIn = () => {
  const loc = useLocation();
  const next = useRef(loc.pathname + loc.search);
  const frame = useRef<HTMLIFrameElement>(null);
  const [src, setSrc] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [height, setHeight] = useState(0);
  const [phase, setPhase] = useState<"loading" | "form" | "finishing">("loading");
  const [error, setError] = useState(false);

  useEffect(() => {
    let live = true;
    setPhase("loading");
    embeddedSignInUrl(next.current).then((u) => live && setSrc(u), () => live && setError(true));
    return () => { live = false; };
  }, [attempt]);

  useEffect(() => {
    const onMessage = (e: MessageEvent<{ type?: string; url?: string }>) => {
      if (e.origin !== location.origin || e.source !== frame.current?.contentWindow) return;
      if (e.data?.type !== SIGNIN_MESSAGE || !e.data.url) return;
      setPhase("finishing");
      completeEmbeddedSignIn(e.data.url).catch(() => { setError(true); setAttempt((a) => a + 1); });
    };
    addEventListener("message", onMessage);
    return () => removeEventListener("message", onMessage);
  }, []);

  // высота — по содержимому формы (тот же origin, документ фрейма доступен)
  useEffect(() => {
    if (phase !== "form") return;
    const doc = frame.current?.contentDocument;
    const box = doc?.querySelector(".login-pf-page") ?? doc?.body;
    if (!box) return;
    const measure = () => setHeight(Math.ceil(box.getBoundingClientRect().height));
    const ro = new ResizeObserver(measure);
    ro.observe(box);
    measure();
    return () => ro.disconnect();
  }, [phase, src]);

  const onLoad = () => {
    const path = frame.current?.contentWindow?.location.pathname;
    setPhase(path === routes.operatorCallback ? "finishing" : "form");
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
          <motion.p variants={fadeUp} className="mt-3 text-pretty text-base text-foreground/70">Логин и код из приложения-аутентификатора</motion.p>
        </motion.div>
        <motion.section variants={fadeUp} initial="hidden" animate="show" className="relative mt-6 overflow-hidden rounded-3xl bg-card/85 p-5 shadow-card backdrop-blur-xl sm:p-8">
          <AnimatePresence initial={false}>
            {error && (
              <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden">
                <div className="mb-4 flex items-center gap-3 rounded-md bg-danger-soft px-4 py-3 text-sm text-danger-soft-foreground">
                  <span className="flex-1">Вход не завершён — попробуйте ещё раз</span>
                  <Button size="sm" variant="quiet" onClick={() => { setError(false); setAttempt((a) => a + 1); }} aria-label="Повторить"><RotateCcw /></Button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
          <motion.div animate={{ height: phase === "form" && height ? height : 148 }} transition={spring.soft} className="relative">
            {src && (
              <iframe
                key={src}
                ref={frame}
                src={src}
                title="Форма входа"
                onLoad={onLoad}
                className={cn("absolute inset-0 size-full border-0 bg-transparent transition-opacity duration-slow", phase !== "form" && "opacity-0")}
              />
            )}
            <AnimatePresence>
              {phase !== "form" && (
                <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="absolute inset-0">
                  {phase === "finishing" ? <div className="grid size-full place-items-center"><Spinner className="size-7" /></div> : <FormSkeleton />}
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        </motion.section>
        <p className="mt-4 text-center text-xs text-muted-foreground">Нет доступа или потерян телефон с кодом — обратитесь к администратору стенда</p>
      </main>
    </div>
  );
};
