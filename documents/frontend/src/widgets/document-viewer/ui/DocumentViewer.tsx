import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ChevronLeft, ChevronRight, RotateCw, ZoomIn, ZoomOut, ImageOff } from "lucide-react";
import type { DocFile, DocumentCard, PageSize } from "@/entities/application";
import { apiBlob } from "@/shared/api";
import { Button, EmptyState, Skeleton } from "@/shared/ui";
import { spring } from "@/shared/config/motion";

export type Highlight = { page?: number | null; bbox?: number[] | null } | null;

const ZOOMS = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];

/** Глобальная страница документа → файл и страница внутри него (активные файлы по порядку). */
const locate = (files: DocFile[], n: number) => {
  let left = n;
  for (const f of files) {
    const p = f.pages ?? 1;
    if (left <= p) return { file: f, page: left };
    left -= p;
  }
  return files[0] ? { file: files[0], page: 1 } : null;
};

/**
 * FE-OP-03: оригинал документа. Страницы — только через intake /files/{id}/content (blob с токеном, без публичных URL),
 * URL.revokeObjectURL при уходе. Зум, поворот, слой bbox поля в системе координат страницы результата.
 */
export const DocumentViewer = ({ doc, highlight }: { doc: DocumentCard; highlight: Highlight }) => {
  const files = useMemo(() => ((doc.files as DocFile[] | undefined) ?? []).filter((f) => f.active !== false), [doc.files]);
  const sizes = (doc.pages as PageSize[] | undefined) ?? [];
  const total = Math.max(1, files.reduce((s, f) => s + (f.pages ?? 1), 0));
  const [page, setPage] = useState(1);
  const [zoom, setZoom] = useState(2);
  const [rot, setRot] = useState(0);
  const target = highlight?.page;
  useEffect(() => { if (target && target <= total) setPage(target); }, [target, total]);
  useEffect(() => { setPage(1); setRot(0); }, [doc.id]);
  const loc = locate(files, Math.min(page, total));
  const q = useQuery({
    queryKey: ["file", loc?.file.id, loc?.page],
    queryFn: ({ signal }) => apiBlob("intake", `/files/${loc!.file.id}/content?page=${loc!.page}`, signal),
    enabled: !!loc,
    staleTime: Infinity,
    gcTime: 60_000,
  });
  const src = useMemo(() => (q.data ? URL.createObjectURL(q.data) : null), [q.data]);
  useEffect(() => () => { if (src) URL.revokeObjectURL(src); }, [src]);
  const size = sizes.find((s) => s.n === page);
  const box = highlight?.bbox && highlight.bbox.length === 4 && size && (highlight.page ?? 1) === page ? highlight.bbox : null;

  if (!files.length) return <EmptyState icon={<ImageOff />} title="Файлов нет" text="Соискатель удалил файл или он ещё загружается" />;
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex flex-wrap items-center gap-1 border-b border-border px-2 py-1.5">
        <Button size="icon-sm" variant="quiet" aria-label="Предыдущая страница" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}><ChevronLeft /></Button>
        <span className="min-w-16 text-center text-sm tabular-nums text-muted-foreground">{page} / {total}</span>
        <Button size="icon-sm" variant="quiet" aria-label="Следующая страница" disabled={page >= total} onClick={() => setPage((p) => p + 1)}><ChevronRight /></Button>
        <span className="mx-1 h-5 w-px bg-border" />
        <Button size="icon-sm" variant="quiet" aria-label="Уменьшить" disabled={zoom <= 0} onClick={() => setZoom((z) => z - 1)}><ZoomOut /></Button>
        <span className="min-w-12 text-center text-sm tabular-nums text-muted-foreground">{Math.round(ZOOMS[zoom] * 100)}%</span>
        <Button size="icon-sm" variant="quiet" aria-label="Увеличить" disabled={zoom >= ZOOMS.length - 1} onClick={() => setZoom((z) => z + 1)}><ZoomIn /></Button>
        <Button size="icon-sm" variant="quiet" aria-label="Повернуть" onClick={() => setRot((r) => (r + 90) % 360)}><RotateCw /></Button>
      </div>
      <div className="min-h-0 flex-1 overflow-auto bg-muted p-4">
        {q.isPending ? <Skeleton className="mx-auto h-96 w-full max-w-xl" />
          : q.isError || !src ? <EmptyState icon={<ImageOff />} title="Страница не открылась" action={<Button size="sm" variant="secondary" onClick={() => q.refetch()}>Повторить</Button>} />
          : (
            <motion.div animate={{ rotate: rot }} transition={spring.soft} className="relative mx-auto origin-center" style={{ width: `${ZOOMS[zoom] * 100}%` }}>
              <img src={src} alt={`Страница ${page}`} className="block w-full select-none rounded-sm bg-card shadow-card" draggable={false} />
              {box && size && (
                <motion.div key={box.join(",")} initial={{ opacity: 0, scale: 1.3 }} animate={{ opacity: 1, scale: 1 }} transition={spring.soft}
                  className="pointer-events-none absolute rounded-xs border-2 border-warning bg-warning/20 ring-4 ring-warning/30"
                  style={{ left: `${(box[0] / size.width) * 100}%`, top: `${(box[1] / size.height) * 100}%`, width: `${((box[2] - box[0]) / size.width) * 100}%`, height: `${((box[3] - box[1]) / size.height) * 100}%` }} />
              )}
            </motion.div>
          )}
      </div>
    </div>
  );
};
