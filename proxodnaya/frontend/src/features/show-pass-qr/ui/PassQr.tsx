import { WifiOff } from "lucide-react";
import { Skeleton, StyledQr, CountdownBar } from "@/shared/ui";
import { windowEndsAt, QR_WINDOW_SEC, cn } from "@/shared/lib";
import { usePassQr } from "../model/usePassQr";

/**
 * QR пропуска на белой плашке (тёмное на белом в обеих темах — так его читает любая камера) и полоса отсчёта.
 * Подпись ставится на телефоне, поэтому код меняется и без сети. При смене код перестраивается волной от центра.
 * tone="brand" — для фирменной заливки (карточка пропуска): белая полоса и светлые подписи.
 */
export const PassQr = ({ slot = "phone", tone = "card" }: { slot?: string; tone?: "card" | "brand" }) => {
  const { qr, left } = usePassQr(slot);
  const brand = tone === "brand";
  return (
    <div className="flex w-full flex-col items-center gap-3">
      <div className={cn("w-full rounded-xl bg-white", brand ? "p-3 shadow-float" : "max-w-72 p-4 shadow-card ring-1 ring-border")}>
        {qr ? <StyledQr value={qr.value} morph label="QR-код пропуска" className="w-full" /> : <Skeleton className="aspect-square w-full rounded-sm" />}
      </div>
      <div className={cn("flex w-full flex-col gap-2", !brand && "max-w-72")}>
        {qr ? <CountdownBar endsAt={windowEndsAt(qr.window)} total={QR_WINDOW_SEC * 1000} className={cn(brand && "bg-white/20")} fillClassName={brand ? "bg-white" : undefined} />
          : <div className={cn("h-1.5 rounded-full", brand ? "bg-white/20" : "bg-surface")} />}
        <div className={cn("flex items-center justify-between gap-3 text-xs", brand ? "text-white/80" : "text-muted-foreground")}>
          <span>Новый код через <span className={cn("font-semibold tabular-nums", brand ? "text-white" : "text-foreground")}>{Math.ceil(left)} с</span></span>
          <span className="inline-flex items-center gap-1"><WifiOff className="size-3.5" />Работает без сети</span>
        </div>
      </div>
    </div>
  );
};
