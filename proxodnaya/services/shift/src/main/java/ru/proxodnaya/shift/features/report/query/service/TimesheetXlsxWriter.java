package ru.proxodnaya.shift.features.report.query.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import ru.proxodnaya.shift.features.report.query.model.Timesheet;
import ru.proxodnaya.shift.features.report.query.model.TimesheetDay;
import ru.proxodnaya.shift.features.report.query.model.TimesheetRow;

/**
 * Табель в XLSX: на человека две строки — коды и часы, слева карточка, справа итоги; лист «Как посчитано».
 */
@Component
public class TimesheetXlsxWriter {

    /** Первая строка таблицы с людьми (после заголовка и двух строк шапки). */
    public static final int FIRST_DATA_ROW = 5;
    /** Столбец первого дня. */
    public static final int FIRST_DAY_COLUMN = 4;

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final List<String> LEFT = List.of("№", "ФИО", "Должность", "Подрядчик");
    private static final List<String> TOTALS = List.of("Дней (Т)", "Часов", "Неявок (Н)", "Неполных дней");
    private static final double MINUTES_IN_HOUR = 60d;
    private static final int HEADER_ROW = 3;
    private static final int NAME_WIDTH = 32;
    private static final int TEXT_WIDTH = 20;
    private static final int DAY_WIDTH = 6;
    private static final int TOTAL_WIDTH = 12;
    private static final int METHOD_WIDTH = 120;
    private static final int CHARS = 256;

    /**
     * Пишет табель.
     *
     * @param sheet табель
     * @return содержимое XLSX
     * @throws UncheckedIOException если POI не смог записать книгу
     */
    public byte[] write(Timesheet sheet) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(book);
            Sheet table = book.createSheet("Табель");
            title(table, sheet, styles);
            header(table, sheet, styles);
            int rowIndex = FIRST_DATA_ROW;
            int number = 1;
            for (TimesheetRow row : sheet.rows()) {
                person(table, rowIndex, number++, row, styles);
                rowIndex += 2;
            }
            legend(table, rowIndex + 1);
            widths(table, sheet.days().size());
            table.createFreezePane(FIRST_DAY_COLUMN, FIRST_DATA_ROW);
            Sheet method = book.createSheet("Как посчитано");
            method.setColumnWidth(0, METHOD_WIDTH * CHARS);
            Cell text = method.createRow(0).createCell(0);
            text.setCellValue(sheet.method());
            text.setCellStyle(styles.wrap);
            book.write(out);
            return out.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("Не удалось сформировать XLSX", error);
        }
    }

    private static void title(Sheet table, Timesheet sheet, Styles styles) {
        Cell title = table.createRow(0).createCell(0);
        title.setCellValue("Табель учёта рабочего времени за " + DATE.format(sheet.from()) + " — " + DATE.format(sheet.to()));
        title.setCellStyle(styles.bold);
        table.createRow(1).createCell(0).setCellValue("Подрядчик: " + (sheet.contractor() == null ? "все" : sheet.contractor())
                + ". Сформирован " + STAMP.format(sheet.generatedAt().atZone(sheet.zone())) + " (" + sheet.zone() + ")");
    }

    private static void header(Sheet table, Timesheet sheet, Styles styles) {
        Row top = table.createRow(HEADER_ROW);
        Row bottom = table.createRow(HEADER_ROW + 1);
        int column = 0;
        for (String name : LEFT) {
            spanned(table, top, bottom, column++, name, styles.header);
        }
        for (LocalDate day : sheet.days()) {
            CellStyle style = weekend(day) ? styles.weekend : styles.header;
            cell(top, column, DAY.format(day), style);
            cell(bottom, column++, day.getDayOfWeek().getDisplayName(TextStyle.SHORT, RU), style);
        }
        for (String name : TOTALS) {
            spanned(table, top, bottom, column++, name, styles.header);
        }
    }

    private static void person(Sheet table, int rowIndex, int number, TimesheetRow row, Styles styles) {
        Row codes = table.createRow(rowIndex);
        Row hours = table.createRow(rowIndex + 1);
        int column = 0;
        spanned(table, codes, hours, column++, Integer.toString(number), styles.text);
        spanned(table, codes, hours, column++, row.person().fullName(), styles.text);
        spanned(table, codes, hours, column++, row.person().position(), styles.text);
        spanned(table, codes, hours, column++, row.person().contractor(), styles.text);
        for (TimesheetDay day : row.days()) {
            cell(codes, column, day.mark().code(), day.incomplete() ? styles.incompleteCode : styles.code);
            Cell value = hours.createCell(column++);
            value.setCellStyle(day.incomplete() ? styles.incompleteHours : styles.hours);
            if (day.minutes() > 0) {
                value.setCellValue(day.minutes() / MINUTES_IN_HOUR);
            }
        }
        spanned(table, codes, hours, column++, row.daysWorked(), styles.code);
        spanned(table, codes, hours, column++, row.minutes() / MINUTES_IN_HOUR, styles.hours);
        spanned(table, codes, hours, column++, row.absences(), styles.code);
        spanned(table, codes, hours, column, row.incomplete(), styles.code);
    }

    private static void legend(Sheet table, int rowIndex) {
        table.createRow(rowIndex).createCell(0).setCellValue("Т — был на объекте; Н — плановая смена без прохода; "
                + "жёлтым — выход не отмечен, часы неполные. Подробно — лист «Как посчитано».");
    }

    private static void widths(Sheet table, int days) {
        table.setColumnWidth(1, NAME_WIDTH * CHARS);
        table.setColumnWidth(2, TEXT_WIDTH * CHARS);
        table.setColumnWidth(LEFT.size() - 1, TEXT_WIDTH * CHARS);
        for (int day = 0; day < days; day++) {
            table.setColumnWidth(FIRST_DAY_COLUMN + day, DAY_WIDTH * CHARS);
        }
        for (int total = 0; total < TOTALS.size(); total++) {
            table.setColumnWidth(FIRST_DAY_COLUMN + days + total, TOTAL_WIDTH * CHARS);
        }
    }

    private static boolean weekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void spanned(Sheet table, Row top, Row bottom, int column, Object value, CellStyle style) {
        Cell cell = top.createCell(column);
        switch (value) {
            case Number number -> cell.setCellValue(number.doubleValue());
            case null -> cell.setBlank();
            default -> cell.setCellValue(value.toString());
        }
        cell.setCellStyle(style);
        bottom.createCell(column).setCellStyle(style);
        table.addMergedRegion(new CellRangeAddress(top.getRowNum(), bottom.getRowNum(), column, column));
    }

    /**
     * Стили книги.
     */
    private static final class Styles {
        private final CellStyle bold;
        private final CellStyle header;
        private final CellStyle weekend;
        private final CellStyle text;
        private final CellStyle code;
        private final CellStyle hours;
        private final CellStyle incompleteCode;
        private final CellStyle incompleteHours;
        private final CellStyle wrap;

        Styles(XSSFWorkbook book) {
            Font font = book.createFont();
            font.setBold(true);
            bold = book.createCellStyle();
            bold.setFont(font);
            header = bordered(book);
            header.setFont(font);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setWrapText(true);
            weekend = bordered(book);
            weekend.cloneStyleFrom(header);
            filled(weekend, IndexedColors.GREY_25_PERCENT);
            text = bordered(book);
            text.setWrapText(true);
            code = bordered(book);
            code.setAlignment(HorizontalAlignment.CENTER);
            hours = bordered(book);
            hours.cloneStyleFrom(code);
            hours.setDataFormat(book.createDataFormat().getFormat("0.00"));
            incompleteCode = bordered(book);
            incompleteCode.cloneStyleFrom(code);
            filled(incompleteCode, IndexedColors.LIGHT_YELLOW);
            incompleteHours = bordered(book);
            incompleteHours.cloneStyleFrom(hours);
            filled(incompleteHours, IndexedColors.LIGHT_YELLOW);
            wrap = book.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);
        }

        private static CellStyle bordered(XSSFWorkbook book) {
            CellStyle style = book.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static void filled(CellStyle style, IndexedColors color) {
            style.setFillForegroundColor(color.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
    }
}
