import { useEffect, useRef, useState } from "react";
import { motion } from "motion/react";
import { api } from "@/shared/api";
import { Button, Spinner, toast } from "@/shared/ui";
import { cn } from "@/shared/lib";
import { spring, tween } from "@/shared/config/motion";

const LEN = 6;

/** Активация телефона по коду приглашения. Ключ создаётся на устройстве, наружу — только публичный. */
export const ActivateForm = ({ initialCode = "", onDone }: { initialCode?: string; onDone: () => void }) => {
  const [code, setCode] = useState(initialCode.toUpperCase().slice(0, LEN));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [focused, setFocused] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const submit = async (c = code) => {
    if (c.length !== LEN || busy) return;
    setBusy(true); setError(null);
    try {
      const w = await api.activateDevice(c);
      toast.success(`Пропуск активирован: ${w.fullName}`);
      onDone();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось активировать пропуск");
    } finally { setBusy(false); }
  };

  useEffect(() => { if (initialCode.length === LEN) submit(initialCode.toUpperCase()); /* авто по ссылке */ }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="flex w-full flex-col items-center gap-6">
      <div className="text-center">
        <h1 className="font-display text-2xl font-semibold tracking-display sm:text-3xl">Код приглашения</h1>
        <p className="mt-2 text-balance text-base text-muted-foreground">6 символов из приглашения или от руководителя</p>
      </div>
      <motion.div onClick={() => input.current?.focus()} animate={error ? { x: [0, -10, 10, -6, 6, 0] } : { x: 0 }} transition={tween.base} className="relative flex w-full max-w-xs gap-1.5 sm:gap-2">
        {Array.from({ length: LEN }, (_, i) => {
          const isCur = focused && i === Math.min(code.length, LEN - 1);
          return (
            <span key={i} className={cn("relative flex h-14 min-w-0 flex-1 items-center justify-center rounded-md border bg-card font-display text-2xl font-semibold transition-colors duration-fast sm:h-16 sm:text-3xl",
              error ? "border-danger" : isCur ? "border-ring ring-4 ring-ring/15" : code[i] ? "border-border-strong" : "border-input")}>
              {code[i] && <motion.span initial={{ scale: 0.4, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={spring.pop}>{code[i]}</motion.span>}
            </span>
          );
        })}
        <input ref={input} value={code} autoFocus inputMode="text" autoCapitalize="characters" autoComplete="one-time-code" aria-label="Код приглашения"
          onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
          onChange={(e) => { const v = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, LEN); setCode(v); setError(null); if (v.length === LEN) submit(v); }}
          className="absolute inset-0 text-base opacity-0" />
      </motion.div>
      {error && <p role="alert" className="max-w-xs text-center text-sm text-danger">{error}</p>}
      <Button size="lg" block className="max-w-xs" disabled={code.length !== LEN || busy} onClick={() => submit()}>{busy ? <Spinner /> : "Активировать пропуск"}</Button>
    </div>
  );
};
