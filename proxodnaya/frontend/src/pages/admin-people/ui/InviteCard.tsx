import { Copy, ExternalLink } from "lucide-react";
import type { Worker } from "@/shared/api";
import { Button, StyledQr, toast } from "@/shared/ui";
import { inviteLink } from "../lib/invite";

export const InviteCard = ({ w }: { w: Worker }) => {
  if (!w.inviteCode) return null;
  const link = inviteLink(w);
  return (
    <div className="flex flex-col items-center gap-5 text-center">
      <div className="w-full max-w-60 rounded-xl bg-white p-4 shadow-card ring-1 ring-border"><StyledQr value={link} label="QR-приглашение" className="w-full" /></div>
      <div>
        <div className="text-sm text-muted-foreground">Код приглашения</div>
        <div className="mt-1 font-mono text-3xl font-medium tracking-widest">{w.inviteCode}</div>
      </div>
      <p className="max-w-xs text-balance text-sm text-muted-foreground">Код одноразовый. Можно продиктовать его, если камера телефона не читает QR.</p>
      <div className="grid w-full max-w-xs grid-cols-2 gap-2">
        <Button variant="secondary" onClick={() => navigator.clipboard.writeText(link).then(() => toast.success("Ссылка скопирована"))}><Copy />Ссылка</Button>
        <a href={link} target="_blank" rel="noreferrer" tabIndex={-1}><Button variant="quiet" block><ExternalLink />Открыть</Button></a>
      </div>
    </div>
  );
};
