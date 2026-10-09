package ru.documents.intake;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/** Файлы для тестов генерируются кодом (rules.md §1: без реальных ПДн). */
public final class TestFiles {

    private TestFiles() {
    }

    /** Изображение-«документ» с надписью. */
    public static byte[] image(String format, int w, int h, String text) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(w / 10, h / 10, w / 4, h / 3);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, h / 15)));
        g.drawString(text, w / 3, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** PDF из n пустых страниц, опционально под паролем. */
    public static byte[] pdf(int pages, boolean encrypted) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            if (encrypted) {
                StandardProtectionPolicy p = new StandardProtectionPolicy("owner", "user", new AccessPermission());
                p.setEncryptionKeyLength(128);
                doc.protect(p);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
