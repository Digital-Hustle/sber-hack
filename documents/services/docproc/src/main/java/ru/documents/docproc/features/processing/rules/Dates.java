package ru.documents.docproc.features.processing.rules;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор дат из документов: ДД.ММ.ГГГГ, ДД месяц ГГГГ, ГГГГ-ММ-ДД, ГГММДД (MRZ).
 */
public final class Dates {

    private static final Pattern NUMERIC = Pattern.compile("(\\d{1,2})\\s*[./\\-]\\s*(\\d{1,2})\\s*[./\\-]\\s*(\\d{2,4})");
    private static final Pattern ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern WORDS = Pattern.compile("(\\d{1,2})\\s+([А-ЯЁA-Z]+)\\.?\\s+(\\d{4})");
    private static final Pattern MRZ = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2})$");
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("ЯНВ", 1), Map.entry("ФЕВ", 2), Map.entry("МАР", 3), Map.entry("АПР", 4), Map.entry("МАЯ", 5),
            Map.entry("МАЙ", 5), Map.entry("ИЮН", 6), Map.entry("ИЮЛ", 7), Map.entry("АВГ", 8), Map.entry("СЕН", 9),
            Map.entry("ОКТ", 10), Map.entry("НОЯ", 11), Map.entry("ДЕК", 12), Map.entry("JAN", 1), Map.entry("FEB", 2),
            Map.entry("MAR", 3), Map.entry("APR", 4), Map.entry("MAY", 5), Map.entry("JUN", 6), Map.entry("JUL", 7),
            Map.entry("AUG", 8), Map.entry("SEP", 9), Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12));

    private Dates() {
    }

    /**
     * Разобрать дату.
     *
     * @param s     текст
     * @param today сегодня (для двухзначного года)
     * @return дата
     */
    public static Optional<LocalDate> parse(String s, LocalDate today) {
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        String t = s.trim().toUpperCase(Locale.ROOT).replace('О', '0').replace('O', '0');
        try {
            Matcher m = ISO.matcher(t);
            if (m.find()) {
                return Optional.of(LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))));
            }
            m = NUMERIC.matcher(t);
            if (m.find()) {
                return Optional.of(LocalDate.of(year(m.group(3), today), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(1))));
            }
            m = WORDS.matcher(s.trim().toUpperCase(Locale.ROOT));
            if (m.find()) {
                String w = m.group(2);
                Integer month = w.length() >= 3 ? MONTHS.get(w.substring(0, 3)) : null;
                if (month != null) {
                    return Optional.of(LocalDate.of(Integer.parseInt(m.group(3)), month, Integer.parseInt(m.group(1))));
                }
            }
            m = MRZ.matcher(t);
            if (m.find()) {
                return Optional.of(LocalDate.of(year(m.group(1), today), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))));
            }
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Дата MRZ ГГММДД; для сроков действия век выбирается вперёд.
     *
     * @param yymmdd  дата MRZ
     * @param today   сегодня
     * @param future  дата может быть в будущем (срок действия)
     * @return дата
     */
    public static Optional<LocalDate> mrz(String yymmdd, LocalDate today, boolean future) {
        if (yymmdd == null || !yymmdd.matches("\\d{6}")) {
            return Optional.empty();
        }
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int base = today.getYear() % 100;
        int limit = future ? base + 50 : base;
        int year = yy <= limit ? 2000 + yy : 1900 + yy;
        try {
            return Optional.of(LocalDate.of(year, Integer.parseInt(yymmdd.substring(2, 4)),
                    Integer.parseInt(yymmdd.substring(4, 6))));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static int year(String y, LocalDate today) {
        int v = Integer.parseInt(y);
        if (y.length() == 4) {
            return v;
        }
        int base = today.getYear() % 100;
        return v <= base + 10 ? 2000 + v : 1900 + v;
    }
}
