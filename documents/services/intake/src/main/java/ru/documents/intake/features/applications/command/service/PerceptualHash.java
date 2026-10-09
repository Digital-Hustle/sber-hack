package ru.documents.intake.features.applications.command.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * DHash 64 бит для почти-дубликатов снимков (FR-03): похожие снимки отличаются на несколько бит.
 */
public final class PerceptualHash {

    public static final int NEAR_DUPLICATE_BITS = 6;
    private static final int W = 9;
    private static final int H = 8;
    private static final int BYTE = 0xFF;
    private static final int R_SHIFT = 16;
    private static final int G_SHIFT = 8;

    private PerceptualHash() {
    }

    /**
     * Хэш изображения.
     *
     * @param src изображение
     * @return 64 бита
     */
    public static long dhash(BufferedImage src) {
        BufferedImage small = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, W, H, null);
        g.dispose();
        long hash = 0;
        int bit = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W - 1; x++) {
                if (gray(small.getRGB(x, y)) > gray(small.getRGB(x + 1, y))) {
                    hash |= 1L << bit;
                }
                bit++;
            }
        }
        return hash;
    }

    /**
     * Расстояние Хэмминга.
     *
     * @param a хэш
     * @param b хэш
     * @return число разных бит
     */
    public static int distance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    private static int gray(int rgb) {
        return ((rgb >> R_SHIFT) & BYTE) + ((rgb >> G_SHIFT) & BYTE) + (rgb & BYTE);
    }
}
