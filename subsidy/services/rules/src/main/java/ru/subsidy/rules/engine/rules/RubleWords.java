package ru.subsidy.rules.engine.rules;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор суммы прописью по-русски: «Один миллион двести тысяч рублей 50 копеек» → 1200000.50.
 * Копейки — цифрами или словами; неизвестное слово → пусто (правило не гадает).
 */
final class RubleWords {

    private static final Map<String, Integer> NUMBERS = Map.ofEntries(
            Map.entry("ноль", 0), Map.entry("один", 1), Map.entry("одна", 1), Map.entry("два", 2),
            Map.entry("две", 2), Map.entry("три", 3), Map.entry("четыре", 4), Map.entry("пять", 5),
            Map.entry("шесть", 6), Map.entry("семь", 7), Map.entry("восемь", 8), Map.entry("девять", 9),
            Map.entry("десять", 10), Map.entry("одиннадцать", 11), Map.entry("двенадцать", 12),
            Map.entry("тринадцать", 13), Map.entry("четырнадцать", 14), Map.entry("пятнадцать", 15),
            Map.entry("шестнадцать", 16), Map.entry("семнадцать", 17), Map.entry("восемнадцать", 18),
            Map.entry("девятнадцать", 19), Map.entry("двадцать", 20), Map.entry("тридцать", 30),
            Map.entry("сорок", 40), Map.entry("пятьдесят", 50), Map.entry("шестьдесят", 60),
            Map.entry("семьдесят", 70), Map.entry("восемьдесят", 80), Map.entry("девяносто", 90),
            Map.entry("сто", 100), Map.entry("двести", 200), Map.entry("триста", 300),
            Map.entry("четыреста", 400), Map.entry("пятьсот", 500), Map.entry("шестьсот", 600),
            Map.entry("семьсот", 700), Map.entry("восемьсот", 800), Map.entry("девятьсот", 900));
    private static final long THOUSAND = 1_000L;
    private static final long MILLION = 1_000_000L;
    private static final long BILLION = 1_000_000_000L;
    private static final int KOPECK_SCALE = 2;
    private static final Pattern SPLIT = Pattern.compile("руб[а-яё]*", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
    private static final Pattern KOPECKS = Pattern.compile("(\\d{1,2})\\s*коп");
    private static final String SPACE = " ";
    private static final Pattern DIGITS_ONLY = Pattern.compile("[0-9 ]+");
    private static final Pattern NOISE = Pattern.compile("[^а-яё0-9 ]");

    private RubleWords() {
    }

    /**
     * Сумма из текста прописью.
     *
     * @param text текст, например «Сто двадцать тысяч рублей 00 копеек»
     * @return сумма со scale 2 или пусто, если текст не разобран
     */
    static Optional<BigDecimal> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String lower = NOISE.matcher(text.toLowerCase(Locale.ROOT).replace('ё', 'е')).replaceAll(SPACE);
        String[] parts = SPLIT.split(lower, 2);
        Optional<Long> rubles = rubles(parts[0].trim());
        Optional<Long> kopecks = parts.length > 1 ? kopecks(parts[1]) : Optional.of(0L);
        return rubles.isEmpty() || kopecks.isEmpty() ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(rubles.get()).add(BigDecimal.valueOf(kopecks.get(), KOPECK_SCALE)));
    }

    private static Optional<Long> rubles(String head) {
        return DIGITS_ONLY.matcher(head).matches() ? Optional.of(Long.parseLong(head.replace(SPACE, ""))) : words(head);
    }

    private static Optional<Long> kopecks(String tail) {
        if (tail.isBlank()) {
            return Optional.of(0L);
        }
        Matcher digits = KOPECKS.matcher(tail);
        return digits.find() ? Optional.of(Long.parseLong(digits.group(1))) : words(tail.replaceAll("коп[а-я]*", SPACE));
    }

    private static Optional<Long> words(String text) {
        long total = 0;
        long group = 0;
        boolean any = false;
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            long scale = scale(word);
            if (scale > 0) {
                total += Math.max(group, 1) * scale;
                group = 0;
            } else if (NUMBERS.containsKey(word)) {
                group += NUMBERS.get(word);
            } else if (word.chars().allMatch(Character::isDigit)) {
                group += Long.parseLong(word);
            } else {
                return Optional.empty();
            }
            any = true;
        }
        return any ? Optional.of(total + group) : Optional.empty();
    }

    private static long scale(String word) {
        if (word.startsWith("тысяч")) {
            return THOUSAND;
        }
        if (word.startsWith("миллион")) {
            return MILLION;
        }
        return word.startsWith("миллиард") ? BILLION : 0;
    }
}
