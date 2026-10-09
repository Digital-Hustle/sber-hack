package ru.documents.intake.features.applications.command.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import ru.documents.intake.config.IntakeProperties;
import ru.documents.intake.features.applications.model.Inspection;
import ru.documents.intake.shared.Codes;

/**
 * Серверные проверки файла (FR-01, Д2): пустой, размер, тип по сигнатуре (не по расширению), целостность,
 * шифрование PDF, число страниц, разрешение. Каждый отказ — свой код из API.md.
 */
@Component
public class FileInspector {

    public static final String PDF = "application/pdf";
    private static final Set<String> RASTER = Set.of("image/jpeg", "image/png", "image/tiff");
    private static final Set<String> HEIC = Set.of("image/heic", "image/heif", "image/heic-sequence");
    private static final int MB = 1024 * 1024;
    private static final int THUMB = 512;

    private final Tika tika = new Tika();
    private final IntakeProperties props;

    public FileInspector(IntakeProperties props) {
        this.props = props;
    }

    /**
     * Проверить файл.
     *
     * @param bytes содержимое
     * @return что это за файл
     */
    public Inspection inspect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw Codes.reject("FILE_EMPTY", 400, "Файл пустой", "Выберите файл ещё раз");
        }
        if (bytes.length > (long) props.maxMb() * MB) {
            throw Codes.reject("FILE_TOO_LARGE", 413, "Файл больше " + props.maxMb() + " МБ",
                    "Сожмите фото или загрузите PDF меньшего размера");
        }
        String mime = tika.detect(bytes);
        String sha = sha256(bytes);
        if (PDF.equals(mime)) {
            return new Inspection(mime, pdfPages(bytes), null, null, null, sha);
        }
        if (RASTER.contains(mime)) {
            return raster(bytes, mime, sha);
        }
        if (HEIC.contains(mime)) {
            return new Inspection("image/heic", 1, null, null, null, sha);
        }
        throw Codes.reject("FILE_TYPE_UNSUPPORTED", 415, "Такой формат не поддерживается",
                "Подойдут PDF, JPG, PNG, HEIC, TIFF");
    }

    private int pdfPages(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.isEncrypted()) {
                throw encrypted();
            }
            int n = doc.getNumberOfPages();
            if (n == 0) {
                throw corrupted();
            }
            if (n > props.maxPages()) {
                throw tooManyPages();
            }
            return n;
        } catch (InvalidPasswordException e) {
            throw encrypted();
        } catch (IOException e) {
            throw corrupted();
        }
    }

    private Inspection raster(byte[] bytes, String mime, String sha) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) {
                throw corrupted();
            }
            ImageReader r = it.next();
            try {
                r.setInput(in);
                int w = r.getWidth(0);
                int h = r.getHeight(0);
                int pages = "image/tiff".equals(mime) ? r.getNumImages(true) : 1;
                if (pages > props.maxPages()) {
                    throw tooManyPages();
                }
                ImageReadParam p = r.getDefaultReadParam();
                int step = Math.max(1, Math.max(w, h) / THUMB);
                p.setSourceSubsampling(step, step, 0, 0);
                BufferedImage img = r.read(0, p);
                if (img == null) {
                    throw corrupted();
                }
                if (Math.min(w, h) < props.imageMinPx()) {
                    throw Codes.reject("IMAGE_TOO_SMALL", 422, "Слишком маленькое изображение",
                            "Снимите ближе, весь документ в кадре");
                }
                return new Inspection(mime, pages, w, h, PerceptualHash.dhash(img), sha);
            } finally {
                r.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ru.documents.common.exception.DomainException de) {
                throw de;
            }
            throw corrupted();
        }
    }

    private ru.documents.common.exception.DomainException tooManyPages() {
        return Codes.reject("TOO_MANY_PAGES", 422, "Слишком много страниц (" + props.maxPages() + ")",
                "Загрузите только страницы нужного документа");
    }

    private static ru.documents.common.exception.DomainException encrypted() {
        return Codes.reject("PDF_ENCRYPTED", 422, "PDF защищён паролем", "Снимите пароль или загрузите фото документа");
    }

    private static ru.documents.common.exception.DomainException corrupted() {
        return Codes.reject("FILE_CORRUPTED", 422, "Файл повреждён и не открывается",
                "Сохраните или снимите документ заново");
    }

    private static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
