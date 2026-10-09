"""Конвейер /v1/extract: формат → страницы → текстовый слой или OCR → качество → MRZ."""

from __future__ import annotations

import logging
import time

import pytesseract
from PIL import Image

from ocr import errors, mrz
from ocr.config import Settings
from ocr.formats import HEIC, JPEG, PDF, PNG, TIFF, WEBP, sniff
from ocr.images import pdf_page_count, raster_pages, render_pdf_page
from ocr.metrics import PAGE_SECONDS, PAGES
from ocr.models import ExtractResult, Mrz, Page
from ocr.preprocess import Gray, prepare, to_gray
from ocr.quality import measure
from ocr.tesseract import best_orientation, recognize
from ocr.textlayer import read_text_layer

log = logging.getLogger("ocr")

_RASTER = {JPEG, PNG, TIFF, HEIC, WEBP}
_MRZ_WHITELIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"


def detect(data: bytes, s: Settings) -> str:
    if not data:
        raise errors.OcrError("FILE_EMPTY", 400, "Файл пустой")
    if len(data) > s.max_mb * 1024 * 1024:
        raise errors.OcrError("FILE_TOO_LARGE", 413, f"Файл больше {s.max_mb} МБ")
    mime = sniff(data)
    if mime is None or (mime != PDF and mime not in _RASTER):
        raise errors.unsupported()
    return mime


def _mrz_from(gray: Gray, s: Settings) -> Mrz | None:
    """MRZ обычно внизу страницы: сначала нижняя треть, затем вся страница."""
    h = gray.shape[0]
    best: Mrz | None = None
    for zone in (gray[int(h * 0.6) :, :], gray):
        try:
            page = recognize(zone, "eng", s.page_timeout_s, psm=6, whitelist=_MRZ_WHITELIST)
        except RuntimeError:
            return best
        found = mrz.find(page.text)
        if found is not None and (best is None or found.valid):
            best = found
            if found.valid:
                return best
    return best


def _ocr_page(n: int, img: Image.Image, dpi: int, s: Settings) -> tuple[Page, Gray | None]:
    started = time.monotonic()
    try:
        gray, _ = prepare(img)
        res = best_orientation(gray, s.langs, s.page_timeout_s)
        h, w = res.image.shape
        q = measure(to_gray(img) if res.rotation == 0 else res.image, res.words, dpi)
        page = Page(
            n=n,
            width=w,
            height=h,
            textLayer=False,
            text=res.text,
            words=res.words,
            quality=q,
            meanConf=res.mean_conf,
            rotation=res.rotation,
        )
        PAGES.labels("ocr").inc()
        return page, res.image
    except RuntimeError as e:
        code = "TIMEOUT" if "timeout" in str(e).lower() else "OCR_FAILED"
        log.warning("page %s failed: %s", n, code)
        return Page(n=n, width=img.width, height=img.height, textLayer=False, text="", words=[], error=code), None
    except pytesseract.TesseractError:
        log.warning("page %s failed: OCR_FAILED", n)
        return Page(
            n=n, width=img.width, height=img.height, textLayer=False, text="", words=[], error="OCR_FAILED"
        ), None
    finally:
        PAGE_SECONDS.observe(time.monotonic() - started)


def extract(data: bytes, s: Settings) -> ExtractResult:
    started = time.monotonic()
    mime = detect(data, s)
    pages: list[Page] = []
    grays: list[Gray] = []
    if mime == PDF:
        count = pdf_page_count(data)
        if count == 0:
            raise errors.corrupted()
        if count > s.max_pages:
            raise errors.too_many_pages(s.max_pages)
        layer = read_text_layer(data, s.dpi, s.max_side_px)
        for i in range(count):
            tp = layer[i] if i < len(layer) else None
            if tp is not None and len(tp.text.strip()) >= s.text_layer_min_chars:
                pages.append(
                    Page(
                        n=i + 1,
                        width=tp.width,
                        height=tp.height,
                        textLayer=True,
                        text=tp.text,
                        words=tp.words,
                        meanConf=1.0,
                    )
                )
                PAGES.labels("text_layer").inc()
                continue
            page, gray = _ocr_page(i + 1, render_pdf_page(data, i, s.dpi, s.max_side_px), s.dpi, s)
            pages.append(page)
            if gray is not None:
                grays.append(gray)
    else:
        for i, (img, dpi) in enumerate(raster_pages(data, s.max_pages, s.max_side_px)):
            page, gray = _ocr_page(i + 1, img, dpi, s)
            pages.append(page)
            if gray is not None:
                grays.append(gray)
    found: Mrz | None = mrz.find("\n".join(p.text for p in pages))
    if found is None or not found.valid:
        for g in grays:
            candidate = _mrz_from(g, s)
            if candidate is not None and (found is None or candidate.valid):
                found = candidate
                if candidate.valid:
                    break
    took = int((time.monotonic() - started) * 1000)
    log.info("extract mime=%s pages=%d words=%d ms=%d", mime, len(pages), sum(len(p.words) for p in pages), took)
    return ExtractResult(mime=mime, pages=pages, mrz=found, durationMs=took)
