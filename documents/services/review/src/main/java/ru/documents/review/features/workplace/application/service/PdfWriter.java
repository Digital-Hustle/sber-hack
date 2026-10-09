package ru.documents.review.features.workplace.application.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Простой PDF A4 для выгрузки карточки (B-05): заголовки, строки и таблицы с переносом слов и страниц.
 * Кириллица — встроенный шрифт Liberation Sans (SIL OFL 1.1, {@code resources/fonts}).
 */
final class PdfWriter implements AutoCloseable {

    private static final float MARGIN = 48;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;

    private final PDDocument doc = new PDDocument();
    private final PDType0Font regular;
    private final PDType0Font bold;
    private PDPageContentStream cs;
    private float y;

    PdfWriter() {
        try {
            regular = font("/fonts/LiberationSans-Regular.ttf");
            bold = font("/fonts/LiberationSans-Bold.ttf");
            newPage();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PDType0Font font(String path) throws IOException {
        try (InputStream in = PdfWriter.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("нет шрифта " + path);
            }
            return PDType0Font.load(doc, in);
        }
    }

    private void newPage() throws IOException {
        if (cs != null) {
            cs.close();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        cs = new PDPageContentStream(doc, page);
        y = PDRectangle.A4.getHeight() - MARGIN;
    }

    private void ensure(float h) throws IOException {
        if (y - h < MARGIN) {
            newPage();
        }
    }

    /** Заголовок. */
    void title(String text, float size) {
        write(text, bold, size, 0, WIDTH, size * 0.6f);
    }

    /** Абзац. */
    void line(String text) {
        write(text, regular, 10.5f, 0, WIDTH, 2);
    }

    /** Таблица: ширины колонок в долях, первая строка — шапка. */
    void table(float[] cols, List<String[]> rows) {
        try {
            boolean head = true;
            for (String[] r : rows) {
                PDType0Font f = head ? bold : regular;
                float size = 9.5f;
                List<List<String>> cells = new ArrayList<>();
                int lines = 1;
                for (int i = 0; i < cols.length; i++) {
                    List<String> w = wrap(safe(r[i]), f, size, cols[i] * WIDTH - 8);
                    cells.add(w);
                    lines = Math.max(lines, w.size());
                }
                float h = lines * size * 1.3f + 6;
                ensure(h);
                float x = MARGIN;
                for (int i = 0; i < cols.length; i++) {
                    float cw = cols[i] * WIDTH;
                    cs.addRect(x, y - h, cw, h);
                    float ty = y - 3 - size;
                    for (String s : cells.get(i)) {
                        text(s, f, size, x + 4, ty);
                        ty -= size * 1.3f;
                    }
                    x += cw;
                }
                cs.stroke();
                y -= h;
                head = false;
            }
            y -= 8;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(String text, PDType0Font f, float size, float indent, float width, float after) {
        try {
            for (String s : wrap(safe(text), f, size, width - indent)) {
                ensure(size * 1.4f);
                text(s, f, size, MARGIN + indent, y - size);
                y -= size * 1.4f;
            }
            y -= after;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void text(String s, PDType0Font f, float size, float x, float ty) throws IOException {
        cs.beginText();
        cs.setFont(f, size);
        cs.newLineAtOffset(x, ty);
        cs.showText(s);
        cs.endText();
    }

    /** Перенос по словам; слишком длинное слово режется. */
    static List<String> wrap(String text, PDType0Font f, float size, float width) throws IOException {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = cur.isEmpty() ? word : cur + " " + word;
                if (f.getStringWidth(next) / 1000 * size <= width) {
                    cur = new StringBuilder(next);
                    continue;
                }
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                }
                cur = new StringBuilder(word);
                while (f.getStringWidth(cur.toString()) / 1000 * size > width && cur.length() > 1) {
                    int cut = cur.length() - 1;
                    while (cut > 1 && f.getStringWidth(cur.substring(0, cut)) / 1000 * size > width) {
                        cut--;
                    }
                    out.add(cur.substring(0, cut));
                    cur = new StringBuilder(cur.substring(cut));
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    /** Символы, которых нет в шрифте (эмодзи, управляющие), заменяются — PDFBox на них падает. */
    private String safe(String s) {
        if (s == null) {
            return "—";
        }
        StringBuilder b = new StringBuilder();
        s.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            try {
                if (cp == '\n' || !Character.isISOControl(cp) && regular.hasGlyph(cp)) {
                    b.append(ch);
                } else {
                    b.append(Character.isWhitespace(cp) ? " " : "?");
                }
            } catch (IOException e) {
                b.append('?');
            }
        });
        return b.toString();
    }

    /** Готовый PDF. */
    byte[] bytes() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            cs.close();
            cs = null;
            doc.save(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (cs != null) {
                cs.close();
            }
            doc.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
