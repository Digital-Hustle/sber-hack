package ru.proxodnaya.assistant.features.assistant.validator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Числа в тексте: «12,5», «12.5», «1 234», «40 %». Даты, время, id вида {@code w123} и номера пунктов списка — не числа ответа.
 */
public final class NumberExtractor {

    private static final Pattern DATES = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:?\\d{2})?)?"
                    + "|\\b\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}\\b|\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b");
    private static final Pattern LIST_MARKERS = Pattern.compile("(?m)^\\s*\\d{1,2}[.)]\\s");
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\p{L}\\d_.,\\-])(\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+|\\d+)(?:[.,](\\d+))?(?![\\d_])(\\s?%)?");
    private static final String SPACE = " ";
    private static final int PERCENT = 3;
    private static final Pattern GROUP = Pattern.compile("[ \\u00A0\\u202F]");

    private NumberExtractor() {
    }

    /**
     * Числа текста.
     *
     * @param text текст
     * @return числа в порядке появления
     */
    public static List<Found> extract(String text) {
        List<Found> found = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        String clean = LIST_MARKERS.matcher(DATES.matcher(text).replaceAll(SPACE)).replaceAll(SPACE);
        Matcher matcher = NUMBER.matcher(clean);
        while (matcher.find()) {
            String whole = matcher.group(1);
            String fraction = matcher.group(2);
            boolean percent = matcher.group(PERCENT) != null;
            List<BigDecimal> parts = new ArrayList<>();
            for (String part : GROUP.split(whole)) {
                parts.add(new BigDecimal(part));
            }
            String digits = GROUP.matcher(whole).replaceAll("");
            BigDecimal value = new BigDecimal(fraction == null ? digits : digits + "." + fraction);
            found.add(new Found(matcher.group().strip(), value, percent, parts.size() > 1 ? parts : List.of()));
        }
        return found;
    }

    /**
     * Число из текста.
     *
     * @param raw     как написано
     * @param value   значение
     * @param percent со знаком %
     * @param parts   если были пробелы-разделители — части по отдельности («в 3 120» могло быть двумя числами)
     */
    public record Found(String raw, BigDecimal value, boolean percent, List<BigDecimal> parts) {
    }
}
