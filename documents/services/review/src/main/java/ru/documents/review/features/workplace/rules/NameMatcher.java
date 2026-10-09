package ru.documents.review.features.workplace.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Сравнение ФИО из разных документов (B-03): кириллица транслитерируется по ГОСТ Р 52535.1-2006 (ICAO 9303),
 * затем обе стороны приводятся к «скелету», нечувствительному к вариантам транслитерации
 * (KH/H/X, ZH/J, IU/YU, IA/YA, Й/Y/I) и к одной опечатке OCR в длинном слове.
 */
public final class NameMatcher {

    private static final Map<Character, String> GOST = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
            Map.entry('й', "i"), Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "shch"), Map.entry('ъ', "ie"), Map.entry('ы', "y"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "iu"), Map.entry('я', "ia"), Map.entry('ў', "u"), Map.entry('қ', "k"),
            Map.entry('ғ', "g"), Map.entry('ҳ', "h"), Map.entry('ӣ', "i"), Map.entry('ӯ', "u"), Map.entry('ҷ', "j"),
            Map.entry('ә', "a"), Map.entry('ө', "o"), Map.entry('ү', "u"), Map.entry('ң', "n"), Map.entry('і', "i"));

    private static final String[][] SKELETON = {
        {"shch", "sh"}, {"sch", "sh"}, {"kh", "h"}, {"x", "h"}, {"zh", "j"}, {"dzh", "j"}, {"dj", "j"},
        {"yu", "u"}, {"iu", "u"}, {"ju", "u"}, {"ya", "a"}, {"ia", "a"}, {"ja", "a"}, {"yo", "o"}, {"io", "o"},
        {"ie", "e"}, {"ye", "e"}, {"ts", "c"}, {"tc", "c"}, {"w", "v"}, {"q", "k"}, {"y", "i"}, {"'", ""}
    };

    private static final int FUZZY_MIN_LENGTH = 5;

    private NameMatcher() {
    }

    /**
     * Латиница по ГОСТ Р 52535.1 для кириллицы, латиница — без изменений (нижний регистр).
     *
     * @param s строка
     * @return латиница
     */
    public static String translit(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            String t = GOST.get(c);
            b.append(t != null ? t : String.valueOf(c));
        }
        return b.toString();
    }

    /**
     * Слова имени в виде скелетов.
     *
     * @param name ФИО в любой письменности
     * @return скелеты слов
     */
    public static List<String> tokens(String name) {
        List<String> out = new ArrayList<>();
        if (name == null) {
            return out;
        }
        for (String w : translit(name).split("[^a-z']+")) {
            if (w.isBlank()) {
                continue;
            }
            String s = w;
            for (String[] r : SKELETON) {
                s = s.replace(r[0], r[1]);
            }
            out.add(s.replaceAll("(.)\\1+", "$1"));
        }
        return out;
    }

    /**
     * Проверяет, одно ли это лицо. Отчество может отсутствовать в латинской записи:
     * Достаточно, чтобы все слова более короткого имени нашлись в более длинном.
     *
     * @param a ФИО
     * @param b ФИО
     * @return true — совпадают
     */
    public static boolean same(String a, String b) {
        List<String> x = tokens(a);
        List<String> y = tokens(b);
        if (x.isEmpty() || y.isEmpty()) {
            return true;
        }
        List<String> shorter = x.size() <= y.size() ? x : y;
        List<String> longer = x.size() <= y.size() ? y : x;
        List<String> pool = new ArrayList<>(longer);
        for (String t : shorter) {
            String hit = pool.stream().filter(p -> close(t, p)).findFirst().orElse(null);
            if (hit == null) {
                return false;
            }
            pool.remove(hit);
        }
        return true;
    }

    private static boolean close(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        return Math.min(a.length(), b.length()) >= FUZZY_MIN_LENGTH && distance(a, b) <= 1;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
