import { useCallback, useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Camera, CameraOff, QrCode, ScanFace } from "lucide-react";
import { api, type Challenge, type DecisionResult, type TerminalMode } from "@/shared/api";
import { useCamera, useMotionDetect } from "@/shared/hooks";
import { Button, Spinner } from "@/shared/ui";
import { cn, durationRu } from "@/shared/lib";
import { spring, tween, popIn, duration, ease } from "@/shared/config/motion";
import { useQrScanner } from "@/features/scan-qr";
import { ChallengePrompt, sampleFrames } from "@/features/face-challenge";
import { DecisionScreen } from "./DecisionScreen";
import { ScanMark } from "./ScanMark";

type State =
  | { kind: "idle" }
  | { kind: "checking" }
  | { kind: "challenge"; challenge: Challenge; name: string; progress: number }
  | { kind: "deciding" }
  | { kind: "result"; result: DecisionResult };

const CAPTURE_MS = 3500;
const CORNERS = ["left-0 top-0 rounded-tl-lg border-l-4 border-t-4", "right-0 top-0 rounded-tr-lg border-r-4 border-t-4", "bottom-0 left-0 rounded-bl-lg border-b-4 border-l-4", "bottom-0 right-0 rounded-br-lg border-b-4 border-r-4"];
/** Камера и фон сменяют друг друга симметрично: одна и та же длительность и кривая; уход — с паузой, чтобы не мигало. */
const camIn = { duration: duration.slow * 2, ease: ease.inOut } as const;
const camOut = { duration: duration.slow * 2, ease: ease.inOut, delay: duration.fast } as const;

/** Терминал КПП: только сканер. Направление (вход/выход) определяет сервер — ADR-037. */
/** offline — ADR-042: связи нет, QR проверяет сам терминал по снимку допусков. */
export type KioskOffline = { verify: (raw: string) => Promise<DecisionResult>; leftMs: number };

export const KioskTerminal = ({ checkpointId, mode = "QR_FACE", offline }: { checkpointId: string; mode?: TerminalMode; offline?: KioskOffline }) => {
  const faceFirst = mode === "FACE_FIRST";
  const cam = useCamera("user");
  const [state, setState] = useState<State>({ kind: "idle" });
  const [noCamera, setNoCamera] = useState(false);

  useEffect(() => { cam.start(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const cameraOn = cam.state === "on" && !noCamera;
  // Камера работает постоянно (сканер не засыпает), но видео плавно проявляется только при движении в кадре; в покое — живой фон.
  const moving = useMotionDetect(cam.videoRef, cameraOn);
  const showVideo = cameraOn && (moving || state.kind !== "idle");

  const handleQr = useCallback(async (raw: string) => {
    setState({ kind: "checking" });
    try {
      if (offline) { setState({ kind: "result", result: await offline.verify(raw) }); return; }
      const res = await api.kioskScan(raw, checkpointId);
      if (res.kind === "decision") { setState({ kind: "result", result: res.result }); return; }
      setState({ kind: "challenge", challenge: res.challenge, name: res.worker.fullName.split(" ")[1] ?? res.worker.fullName, progress: 0 });
      const started = Date.now();
      const progressId = setInterval(() => setState((s) => (s.kind === "challenge" ? { ...s, progress: Math.min(1, (Date.now() - started) / CAPTURE_MS) } : s)), 100);
      const frames = await sampleFrames(cam.videoRef.current, Math.min(CAPTURE_MS, res.challenge.timeoutMs || CAPTURE_MS));
      clearInterval(progressId);
      setState({ kind: "deciding" });
      setState({ kind: "result", result: await api.kioskFrames(res.token, frames) });
    } catch {
      setState({ kind: "result", result: { attemptId: "-", decision: "ERROR", code: "SYSTEM_ERROR", message: "Сервис недоступен", hint: "Обратитесь к сотруднику охраны", direction: "IN", ts: Date.now() } });
    }
  }, [checkpointId, cam.videoRef, offline]);

  // Режим «Сначала лицо» (ADR-038): челлендж живости → сервер ищет человека по базе. Не узнал — просим QR (сканер работает всегда).
  const runFace = useCallback(async () => {
    const challenge = api.faceChallenge();
    setState({ kind: "challenge", challenge, name: "", progress: 0 });
    const started = Date.now();
    const progressId = setInterval(() => setState((s) => (s.kind === "challenge" ? { ...s, progress: Math.min(1, (Date.now() - started) / CAPTURE_MS) } : s)), 100);
    try {
      const frames = await sampleFrames(cam.videoRef.current, CAPTURE_MS);
      setState({ kind: "deciding" });
      const r = await api.kioskIdentify(checkpointId, frames);
      setState({ kind: "result", result: r.code === "FACE_NOT_FOUND" ? { ...r, message: "Не удалось узнать", hint: "Покажите QR-пропуск — сверим лицо по нему" } : r });
    } catch {
      setState({ kind: "result", result: { attemptId: "-", decision: "ERROR", code: "SYSTEM_ERROR", message: "Сервис недоступен", hint: "Обратитесь к сотруднику охраны", direction: "IN", ts: Date.now() } });
    } finally { clearInterval(progressId); }
  }, [checkpointId, cam.videoRef]);

  // Автозапуск: человек подошёл и стоит перед камерой ~1 с.
  useEffect(() => {
    if (!faceFirst || !cameraOn || !moving || state.kind !== "idle") return;
    const t = setTimeout(runFace, 1000);
    return () => clearTimeout(t);
  }, [faceFirst, cameraOn, moving, state.kind, runFace]);

  useQrScanner(cam.videoRef, cameraOn && state.kind === "idle", handleQr);

  const reset = useCallback(() => setState({ kind: "idle" }), []);

  return (
    <div className="relative isolate flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl">
      <motion.video ref={cam.bindVideo} playsInline muted className="absolute inset-0 size-full -scale-x-100 object-cover" initial={false} animate={{ opacity: showVideo ? 1 : 0, filter: showVideo ? "blur(0px)" : "blur(16px)" }} transition={showVideo ? camIn : camOut} />
      <motion.div aria-hidden className="absolute inset-0 bg-linear-to-t from-black/70 via-black/10 to-black/30" initial={false} animate={{ opacity: showVideo ? 1 : 0 }} transition={showVideo ? camIn : camOut} />

      <div className="relative flex min-h-0 flex-1 flex-col items-center justify-center gap-6 overflow-y-auto p-5 text-center sm:gap-8 sm:p-8">
        <AnimatePresence mode="wait">
          {state.kind === "idle" && (
            <motion.div key="idle" variants={popIn} initial="hidden" animate="show" exit="exit" className="flex w-full flex-col items-center gap-6 sm:gap-8">
              <div className="relative size-44 shrink-0 sm:size-64">
                {CORNERS.map((c) => <span key={c} className={cn("absolute size-10 border-white sm:size-14", c)} />)}
                <ScanMark>{!cameraOn && <QrCode className="size-16 sm:size-20" strokeWidth={1.5} />}</ScanMark>
              </div>
              <div className="relative flex flex-col items-center gap-2">
                {offline && <span className="rounded-2xs bg-warning/20 px-2.5 py-1 text-sm font-medium text-warning">Без связи · проверка на терминале · ещё {durationRu(offline.leftMs)}</span>}
                <h1 className="text-balance font-display text-3xl font-semibold tracking-display text-white sm:text-4xl lg:text-5xl">{faceFirst ? "Посмотрите в камеру" : "Покажите QR-пропуск"}</h1>
                <p className="text-balance text-base text-white/70 sm:text-lg">{faceFirst && cameraOn ? "Или покажите QR-пропуск. Вход или выход определится автоматически" : cameraOn ? (moving ? "Вход или выход определится автоматически" : "Подойдите к камере и поднесите экран телефона") : cam.state === "denied" ? "Доступ к камере запрещён. Обратитесь к сотруднику охраны" : cam.state === "unavailable" ? "Камера не найдена. Обратитесь к сотруднику охраны" : "Камера отключена. Обратитесь к сотруднику охраны"}</p>
              </div>
              {!cameraOn && (
                <div className="relative flex w-full max-w-sm flex-col gap-2 sm:w-auto sm:max-w-none sm:flex-row sm:gap-3">
                  {!noCamera && cam.state !== "on" && <Button variant="secondary" size="lg" onClick={() => cam.start()}><Camera />Включить камеру</Button>}
                  {noCamera && <Button variant="secondary" size="lg" onClick={() => setNoCamera(false)}><Camera />Вернуть камеру</Button>}
                  {faceFirst && <Button variant="secondary" size="lg" onClick={runFace}><ScanFace />Пройти по лицу</Button>}
                </div>
              )}
            </motion.div>
          )}
          {(state.kind === "checking" || state.kind === "deciding") && (
            <motion.div key="busy" variants={popIn} initial="hidden" animate="show" exit="exit" className="flex items-center gap-4 rounded-lg bg-popover/90 px-6 py-5 text-popover-foreground shadow-pop backdrop-blur-md sm:px-8">
              <Spinner className="size-7 border-3 text-brand" />
              <span className="font-display text-xl font-semibold tracking-display sm:text-2xl">{state.kind === "checking" ? "Проверяем пропуск…" : "Сверяем лицо…"}</span>
            </motion.div>
          )}
          {state.kind === "challenge" && (
            <motion.div key="challenge" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={tween.fast} className="flex w-full justify-center">
              <ChallengePrompt challenge={state.challenge} name={state.name} progress={state.progress} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <AnimatePresence>
        {cameraOn && state.kind === "idle" && (
          <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 12 }} transition={spring.soft} className="absolute inset-x-0 bottom-0 z-raised flex justify-center gap-2 pb-4">
            {faceFirst && <Button variant="secondary" size="sm" onClick={runFace} className="bg-black/40 text-white backdrop-blur-md hover:bg-black/55"><ScanFace />Пройти по лицу</Button>}
            <Button variant="secondary" size="sm" onClick={() => setNoCamera(true)} className="bg-black/40 text-white backdrop-blur-md hover:bg-black/55"><CameraOff />Без камеры</Button>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>{state.kind === "result" && <DecisionScreen key={state.result.attemptId + state.result.ts} result={state.result} onDone={reset} />}</AnimatePresence>
    </div>
  );
};
