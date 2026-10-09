// Помощник (assistant): цифры — только из инструментов на сервере, ключ модели живёт на сервере.
// С Accept: text/event-stream — поток tool/delta/done/error (ADR-046), иначе обычный JSON-ответ.
import { httpRaw } from "./http";
import { toApiError } from "./problem";

export type AssistantTable = { columns: string[]; rows: (string | number)[][] };
export type AssistantAnswer = { text: string; sources: string[]; table?: AssistantTable; mode: "llm" | "rules"; tools: string[] };
export type AssistantEvent = { type: "tool"; name: string } | { type: "delta"; text: string };
export type AskOptions = { onEvent?: (e: AssistantEvent) => void; signal?: AbortSignal };

type Source = string | { tool: string; args?: unknown; at?: string | number; ok?: boolean };
type AnswerRs = { text: string; sources?: Source[]; tools?: string[]; table?: AssistantTable | null; mode?: "llm" | "rules"; validation?: string };

const TOOL_TEXT: Record<string, string> = {
  on_site_now: "Кто сейчас на объекте", worked_hours: "Отработанные часы", hours_worked: "Отработанные часы", late_overtime: "Опоздания и переработки",
  late_today: "Опоздания сегодня", refusals: "Отказы на проходной", worker_today: "День сотрудника",
};
const sourceText = (s: Source) => (typeof s === "string" ? s : TOOL_TEXT[s.tool] ?? s.tool);
const toAnswer = (r: AnswerRs): AssistantAnswer => {
  const tools = r.tools ?? (r.sources ?? []).flatMap((s) => (typeof s === "string" ? [] : [s.tool]));
  return { text: r.text, sources: [...new Set((r.sources ?? []).map(sourceText))], table: r.table ?? undefined, mode: r.mode ?? (r.validation === "REFUSED" ? "rules" : "llm"), tools };
};

/** Разбор потока SSE из fetch (EventSource не умеет POST). */
const readStream = async (res: Response, o: AskOptions): Promise<AssistantAnswer> => {
  const reader = res.body!.getReader();
  const dec = new TextDecoder();
  let buf = "";
  let done: AssistantAnswer | null = null;
  for (;;) {
    const { value, done: end } = await reader.read();
    if (end) break;
    buf += dec.decode(value, { stream: true });
    let i: number;
    while ((i = buf.indexOf("\n\n")) >= 0) {
      const block = buf.slice(0, i);
      buf = buf.slice(i + 2);
      let event = "message";
      let data = "";
      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data += line.slice(5).trim();
      }
      if (!data) continue;
      const v = JSON.parse(data) as Record<string, unknown>;
      if (event === "tool") o.onEvent?.({ type: "tool", name: String(v.name) });
      else if (event === "delta") o.onEvent?.({ type: "delta", text: String(v.text ?? "") });
      else if (event === "done") done = toAnswer(v as unknown as AnswerRs);
      else if (event === "error") throw new Error(String(v.detail ?? "Помощник недоступен"));
    }
  }
  if (!done) throw new Error("Ответ оборвался — спросите ещё раз");
  return done;
};

export const ask = async (question: string, history: { role: "user" | "assistant"; text: string }[] = [], o: AskOptions = {}): Promise<AssistantAnswer> => {
  const res = await httpRaw("assistant", "/assistant/ask", { body: { question, history: history.slice(-10) }, headers: { Accept: "text/event-stream, application/json" }, signal: o.signal });
  if (!res.ok) throw await toApiError(res);
  if ((res.headers.get("Content-Type") ?? "").includes("text/event-stream")) return readStream(res, o);
  const a = toAnswer((await res.json()) as AnswerRs);
  o.onEvent?.({ type: "delta", text: a.text });
  return a;
};
