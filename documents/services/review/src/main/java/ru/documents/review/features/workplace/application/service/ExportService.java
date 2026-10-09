package ru.documents.review.features.workplace.application.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import ru.documents.common.exception.DomainException;
import ru.documents.review.config.ReviewProperties;
import ru.documents.review.features.workplace.command.service.ActionService;
import ru.documents.review.features.workplace.model.Actor;
import ru.documents.review.features.workplace.model.CardView;
import ru.documents.review.features.workplace.model.DocumentCard;
import ru.documents.review.features.workplace.model.ExpiringItem;
import ru.documents.review.features.workplace.model.FieldCard;
import ru.documents.review.features.workplace.model.Finding;

/**
 * Выгрузки: карточка соискателя для кадров (B-05, DOCX/XLSX, без сканов) и истекающие документы (B-04, XLSX).
 */
@Service
public class ExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String YES = "да";
    private static final String NO = "нет";

    private final WorkplaceService workplace;
    private final ActionService actions;
    private final ReviewProperties props;

    public ExportService(WorkplaceService workplace, ActionService actions, ReviewProperties props) {
        this.workplace = workplace;
        this.actions = actions;
        this.props = props;
    }

    /**
     * Выгруженный файл.
     *
     * @param name имя
     * @param mime тип
     * @param body содержимое
     */
    public record FileOut(String name, String mime, byte[] body) {
    }

    /**
     * Карточка соискателя.
     *
     * @param id     заявка
     * @param format docx | xlsx
     * @param me     кто выгружает
     * @return файл
     */
    public FileOut card(UUID id, String format, Actor me) {
        CardView card = workplace.card(id, true);
        FileOut out = switch (format == null ? "docx" : format) {
            case "docx" -> new FileOut("applicant-" + id + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx(card));
            case "xlsx" -> new FileOut("applicant-" + id + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(card));
            default -> throw DomainException.badRequest("FORMAT_UNSUPPORTED", "Формат — docx или xlsx",
                    "PDF не поддерживается: лицензия библиотеки не проверена");
        };
        actions.exported(id, format == null ? "docx" : format, me);
        return out;
    }

    /**
     * Истекающие документы в XLSX.
     *
     * @param items строки отчёта
     * @return файл
     */
    public FileOut expiring(List<ExpiringItem> items) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Истекают");
            header(wb, sh, List.of("Соискатель", "Документ", "Действует до", "Осталось дней", "Статус заявки",
                    "Заявка"));
            int r = 1;
            for (ExpiringItem it : items) {
                Row row = sh.createRow(r++);
                row.createCell(0).setCellValue(nvl(it.applicantName()));
                row.createCell(1).setCellValue(nvl(it.typeName()));
                row.createCell(2).setCellValue(it.expiresOn().format(DATE));
                row.createCell(3).setCellValue(it.daysLeft());
                row.createCell(4).setCellValue(nvl(it.status()));
                row.createCell(5).setCellValue(it.applicationId().toString());
            }
            widths(sh, 6);
            wb.write(bos);
            return new FileOut("expiring.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] docx(CardView c) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            XWPFRun title = doc.createParagraph().createRun();
            title.setBold(true);
            title.setFontSize(16);
            title.setText("Карточка соискателя");
            line(doc, "ФИО: " + nvl(c.application().applicantName()));
            line(doc, "Заявка: " + c.application().id() + " · статус: " + c.application().status());
            if (c.decision() != null) {
                line(doc, "Решение: " + c.decision().decision() + " (" + c.decision().actor() + ", "
                        + c.decision().createdAt().atZone(ZoneId.of(props.zone())).format(DATE) + ")");
            }
            for (DocumentCard d : c.documents()) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setBold(true);
                r.setFontSize(13);
                r.setText(nvl(d.typeName()) + (d.confirmed() ? " — подтверждён " + nvl(d.confirmedBy())
                        : " — не подтверждён"));
                if (d.fields().isEmpty()) {
                    line(doc, "Поля не извлечены (стадия " + d.stage() + ")");
                    continue;
                }
                XWPFTable t = doc.createTable(1, 3);
                t.getRow(0).getCell(0).setText("Поле");
                t.getRow(0).getCell(1).setText("Значение");
                t.getRow(0).getCell(2).setText("Исправлено оператором");
                for (FieldCard f : d.fields()) {
                    XWPFTableRow row = t.createRow();
                    row.getCell(0).setText(nvl(f.title()));
                    row.getCell(1).setText(nvl(f.value()));
                    row.getCell(2).setText(f.corrected() ? YES : NO);
                }
            }
            if (!c.findings().isEmpty()) {
                line(doc, "Замечания:");
                for (Finding f : c.findings()) {
                    line(doc, "• " + describe(f));
                }
            }
            doc.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] xlsx(CardView c) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Карточка");
            header(wb, sh, List.of("Документ", "Поле", "Значение", "Исправлено", "Документ подтверждён"));
            int r = 1;
            for (DocumentCard d : c.documents()) {
                for (FieldCard f : d.fields()) {
                    Row row = sh.createRow(r++);
                    row.createCell(0).setCellValue(nvl(d.typeName()));
                    row.createCell(1).setCellValue(nvl(f.title()));
                    row.createCell(2).setCellValue(nvl(f.value()));
                    row.createCell(3).setCellValue(f.corrected() ? YES : NO);
                    row.createCell(4).setCellValue(d.confirmed() ? YES : NO);
                }
            }
            widths(sh, 5);
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void header(SXSSFWorkbook wb, Sheet sh, List<String> titles) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        st.setFont(f);
        Row h = sh.createRow(0);
        for (int i = 0; i < titles.size(); i++) {
            Cell cell = h.createCell(i);
            cell.setCellValue(titles.get(i));
            cell.setCellStyle(st);
        }
    }

    private static void widths(Sheet sh, int n) {
        for (int i = 0; i < n; i++) {
            sh.setColumnWidth(i, 28 * 256);
        }
    }

    private static void line(XWPFDocument doc, String text) {
        doc.createParagraph().createRun().setText(text);
    }

    private static String describe(Finding f) {
        if ("MISSING_DOCUMENT".equals(f.code())) {
            return "Не хватает документа: " + f.params().get("type");
        }
        return f.params().get("field") + " отличается в документах «" + f.params().get("a") + "» и «"
                + f.params().get("b") + "»";
    }

    private static String nvl(String s) {
        return s == null ? "—" : s;
    }
}
