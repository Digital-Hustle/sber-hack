package ru.documents.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import ru.documents.common.exception.DomainException;
import ru.documents.intake.config.IntakeProperties;
import ru.documents.intake.features.applications.command.service.FileInspector;
import ru.documents.intake.features.applications.model.Inspection;

/** FR-01: каждый код отказа приёма — свой тест. */
class FileInspectorTest {

    private final FileInspector inspector = new FileInspector(
            new IntakeProperties(1, 3, 40, 600, "k", "p", 5, "https://localhost", 30));

    private static String code(Runnable r) {
        try {
            r.run();
        } catch (DomainException e) {
            return e.code();
        }
        return "OK";
    }

    @Test
    void acceptsPngAndComputesHash() throws Exception {
        Inspection i = inspector.inspect(TestFiles.image("png", 900, 700, "ПАСПОРТ"));
        assertThat(i.mime()).isEqualTo("image/png");
        assertThat(i.pages()).isEqualTo(1);
        assertThat(i.width()).isEqualTo(900);
        assertThat(i.phash()).isNotNull();
        assertThat(i.sha256()).hasSize(64);
    }

    @Test
    void acceptsJpegAndPdf() throws Exception {
        assertThat(inspector.inspect(TestFiles.image("jpg", 800, 800, "СНИЛС")).mime()).isEqualTo("image/jpeg");
        Inspection pdf = inspector.inspect(TestFiles.pdf(2, false));
        assertThat(pdf.mime()).isEqualTo(FileInspector.PDF);
        assertThat(pdf.pages()).isEqualTo(2);
    }

    @Test
    void rejectsEmpty() {
        assertThat(code(() -> inspector.inspect(new byte[0]))).isEqualTo("FILE_EMPTY");
    }

    @Test
    void rejectsTooLarge() {
        byte[] big = new byte[1024 * 1024 + 1];
        assertThat(code(() -> inspector.inspect(big))).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void rejectsByContentNotExtension() {
        byte[] text = "это не картинка, а текст".getBytes(StandardCharsets.UTF_8);
        assertThat(code(() -> inspector.inspect(text))).isEqualTo("FILE_TYPE_UNSUPPORTED");
    }

    @Test
    void rejectsCorruptedImage() throws Exception {
        byte[] png = TestFiles.image("png", 900, 700, "X");
        byte[] cut = Arrays.copyOf(png, 120);
        assertThat(code(() -> inspector.inspect(cut))).isEqualTo("FILE_CORRUPTED");
    }

    @Test
    void rejectsCorruptedPdf() {
        byte[] pdf = "%PDF-1.7\n1 0 obj garbage".getBytes(StandardCharsets.US_ASCII);
        assertThat(code(() -> inspector.inspect(pdf))).isEqualTo("FILE_CORRUPTED");
    }

    @Test
    void rejectsEncryptedPdf() throws Exception {
        byte[] pdf = TestFiles.pdf(1, true);
        assertThat(code(() -> inspector.inspect(pdf))).isEqualTo("PDF_ENCRYPTED");
    }

    @Test
    void rejectsTooManyPages() throws Exception {
        byte[] pdf = TestFiles.pdf(4, false);
        assertThat(code(() -> inspector.inspect(pdf))).isEqualTo("TOO_MANY_PAGES");
    }

    @Test
    void rejectsTooSmallImage() throws Exception {
        byte[] small = TestFiles.image("png", 400, 300, "X");
        assertThatThrownBy(() -> inspector.inspect(small)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).status()).isEqualTo(422);
        assertThat(code(() -> inspector.inspect(small))).isEqualTo("IMAGE_TOO_SMALL");
    }

    @Test
    void similarImagesHaveCloseHashes() throws Exception {
        long a = inspector.inspect(TestFiles.image("png", 900, 700, "ПАСПОРТ")).phash();
        long b = inspector.inspect(TestFiles.image("jpg", 900, 700, "ПАСПОРТ")).phash();
        assertThat(Long.bitCount(a ^ b)).isLessThanOrEqualTo(10);
    }
}
