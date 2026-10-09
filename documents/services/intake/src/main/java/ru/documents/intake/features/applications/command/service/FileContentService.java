package ru.documents.intake.features.applications.command.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.documents.common.exception.DomainException;
import ru.documents.intake.features.applications.command.repository.ApplicationRepository;
import ru.documents.intake.features.applications.model.FileContent;
import ru.documents.intake.features.applications.query.repository.ApplicationReadRepository;
import ru.documents.intake.features.applications.query.repository.ApplicationReadRepository.FileRow;

/**
 * Выдача файлов только через сервер (FR-62): права проверены выше, каждый просмотр — в аудит.
 * Для просмотрщика PDF и TIFF отдаются постранично в PNG.
 */
@Service
public class FileContentService {

    private static final float VIEW_DPI = 110f;

    private final ApplicationReadRepository read;
    private final ApplicationRepository repo;
    private final FileCrypto crypto;
    private final Clock clock;

    public FileContentService(ApplicationReadRepository read, ApplicationRepository repo, FileCrypto crypto,
                              Clock clock) {
        this.read = read;
        this.repo = repo;
        this.crypto = crypto;
        this.clock = clock;
    }

    /**
     * Заявка, которой принадлежит файл.
     *
     * @param fileId файл
     * @return заявка
     */
    @Transactional(readOnly = true)
    public UUID owner(UUID fileId) {
        return read.file(fileId).map(FileRow::applicationId).orElseThrow(DomainException::notFound);
    }

    /**
     * Может ли сотрудник открыть скан (B-07): старший — любой, оператор — только назначенной ему заявки.
     *
     * @param fileId   файл
     * @param username логин
     * @param senior   роль SENIOR_OPERATOR
     */
    @Transactional(readOnly = true)
    public void ensureStaffAccess(UUID fileId, String username, boolean senior) {
        UUID app = owner(fileId);
        if (senior) {
            return;
        }
        if (read.assignee(app).filter(username::equals).isEmpty()) {
            throw new DomainException("FILE_NOT_ASSIGNED", 403, "Скан открывает только оператор, которому назначена заявка",
                    "Возьмите заявку в работу");
        }
    }

    /**
     * Исходный файл (для docproc).
     *
     * @param fileId файл
     * @param actor  кто
     * @return содержимое
     */
    @Transactional
    public FileContent original(UUID fileId, String actor) {
        FileRow f = read.file(fileId).orElseThrow(DomainException::notFound);
        repo.logAccess(fileId, actor, "SERVICE", clock.instant());
        return new FileContent(crypto.decrypt(f.enc()), f.mime(), f.pages());
    }

    /**
     * Страница для просмотра.
     *
     * @param fileId файл
     * @param page   номер с 1 (null — первая)
     * @param actor  кто смотрит
     * @param kind   OPERATOR | APPLICANT
     * @return картинка страницы
     */
    @Transactional
    public FileContent page(UUID fileId, Integer page, String actor, String kind) {
        FileRow f = read.file(fileId).orElseThrow(DomainException::notFound);
        repo.logAccess(fileId, actor, kind, clock.instant());
        byte[] plain = crypto.decrypt(f.enc());
        int n = page == null ? 1 : page;
        if (n < 1 || n > f.pages()) {
            throw DomainException.notFound();
        }
        try {
            if (FileInspector.PDF.equals(f.mime())) {
                try (PDDocument doc = Loader.loadPDF(plain)) {
                    BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(n - 1, VIEW_DPI, ImageType.RGB);
                    return new FileContent(png(img), "image/png", f.pages());
                }
            }
            if ("image/tiff".equals(f.mime())) {
                try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(plain))) {
                    var r = ImageIO.getImageReaders(in).next();
                    r.setInput(in);
                    BufferedImage img = r.read(n - 1);
                    r.dispose();
                    return new FileContent(png(img), "image/png", f.pages());
                }
            }
        } catch (IOException e) {
            throw new DomainException("FILE_CORRUPTED", 422, "Не удалось показать страницу", "Скачайте оригинал");
        }
        return new FileContent(plain, f.mime(), f.pages());
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
