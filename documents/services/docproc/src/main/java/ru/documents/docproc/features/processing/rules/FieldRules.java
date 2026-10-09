package ru.documents.docproc.features.processing.rules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import ru.documents.docproc.features.processing.model.FieldResult.Check;

/**
 * Нормализация значения поля и детерминированные проверки по виду поля ({@code x-check} в схеме).
 */
public final class FieldRules {

    public static final String FORMAT = "FORMAT";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_AGE = 100;

    private FieldRules() {
    }

    /**
     * Нормализованное значение и проверки.
     *
     * @param value  значение
     * @param checks проверки
     * @param date   разобранная дата (для дат)
     */
    public record Normalized(String value, List<Check> checks, LocalDate date) {
    }

    /**
     * Нормализовать и проверить.
     *
     * @param kind  вид поля
     * @param value значение модели
     * @param today сегодня
     * @return итог
     */
    public static Normalized apply(String kind, String value, LocalDate today) {
        String v = Text.clean(value);
        List<Check> checks = new ArrayList<>();
        if (v == null || v.isEmpty()) {
            return new Normalized(null, checks, null);
        }
        switch (kind == null ? "text" : kind) {
            case "date_birth", "date_issue", "date_expiry", "date_entry", "date_until" -> {
                Optional<LocalDate> d = Dates.parse(v, today);
                checks.add(new Check(FORMAT, d.isPresent()));
                if (d.isEmpty()) {
                    return new Normalized(v, checks, null);
                }
                LocalDate date = d.get();
                switch (kind) {
                    case "date_birth" -> checks.add(new Check("DATE_RANGE",
                            date.isBefore(today) && date.isAfter(today.minusYears(MAX_AGE))));
                    case "date_issue", "date_entry" -> checks.add(new Check("NOT_IN_FUTURE", !date.isAfter(today)));
                    case "date_expiry", "date_until" -> checks.add(new Check("NOT_EXPIRED", !date.isBefore(today)));
                    default -> {
                        // Для прочих дат дополнительных проверок нет.
                    }
                }
                return new Normalized(date.format(ISO), checks, date);
            }
            case "snils" -> {
                String d = Text.digits(v);
                boolean ok = Checksums.snils(d);
                checks.add(new Check("SNILS_CHECKSUM", ok));
                String out = d.length() == 11
                        ? d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6, 9) + " " + d.substring(9) : v;
                return new Normalized(out, checks, null);
            }
            case "inn" -> {
                String d = Text.digits(v);
                checks.add(new Check("INN_CHECKSUM", Checksums.innPerson(d)));
                return new Normalized(d.isEmpty() ? v : d, checks, null);
            }
            case "passport_rf_series" -> {
                String d = Text.digits(v);
                checks.add(new Check(FORMAT, d.length() == 4));
                return new Normalized(d.length() == 4 ? d : v, checks, null);
            }
            case "passport_rf_number" -> {
                String d = Text.digits(v);
                checks.add(new Check(FORMAT, d.length() == 6));
                return new Normalized(d.length() == 6 ? d : v, checks, null);
            }
            case "dept_code" -> {
                String d = Text.digits(v);
                checks.add(new Check(FORMAT, d.length() == 6));
                return new Normalized(d.length() == 6 ? d.substring(0, 3) + "-" + d.substring(3) : v, checks, null);
            }
            case "sex" -> {
                String k = Text.key(v);
                String s = k.startsWith("М") || k.startsWith("M") ? "M" : k.startsWith("Ж") || k.startsWith("F") ? "F" : v;
                checks.add(new Check(FORMAT, "M".equals(s) || "F".equals(s)));
                return new Normalized(s, checks, null);
            }
            case "name" -> {
                return new Normalized(v.toUpperCase(Locale.ROOT), checks, null);
            }
            case "doc_number" -> {
                return new Normalized(v.toUpperCase(Locale.ROOT), checks, null);
            }
            default -> {
                return new Normalized(v, checks, null);
            }
        }
    }
}
