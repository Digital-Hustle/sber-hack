import type { Worker } from "@/shared/api";
import { absoluteUrl, routes } from "@/shared/const/router";

/** Ссылка-инвайт: только одноразовый код. Кого привязывает телефон, сервер знает по коду (POST /devices/activate). */
export const inviteLink = (w: Pick<Worker, "inviteCode">) => absoluteUrl(`${routes.workerActivate}?c=${encodeURIComponent(w.inviteCode ?? "")}`);
