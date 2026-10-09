package ru.documents.docproc.features.processing.rules;

import java.util.Locale;

/**
 * Нормализация строк для сравнения (верхний регистр, ё→е, только буквы и цифры).
 */
public final class Text {

    private Text() {
    }

    /**
     * Ключ сравнения.
     *
     * @param s строка
     * @return ключ
     */
    public static String key(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toUpperCase(Locale.ROOT).replace('Ё', 'Е').toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Только цифры (с исправлением типичных ошибок OCR О→0, З→3, I/l→1).
     *
     * @param s строка
     * @return цифры
     */
    public static String digits(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            char d = switch (c) {
                case 'О', 'O', 'о', 'o' -> '0';
                case 'З', 'з' -> '3';
                case 'I', 'l', '|' -> '1';
                default -> c;
            };
            if (Character.isDigit(d)) {
                b.append(d);
            }
        }
        return b.toString();
    }

    /**
     * Схлопнуть пробелы.
     *
     * @param s строка
     * @return строка
     */
    public static String clean(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }

    /**
     * Транслитерация кириллицы в латиницу (ICAO 9303 / ГОСТ Р 52535.1).
     *
     * @param s строка
     * @return латиница
     */
    public static String latin(String s) {
        String k = key(s);
        StringBuilder b = new StringBuilder();
        for (char c : k.toCharArray()) {
            b.append(switch (c) {
                case 'А' -> "A"; case 'Б' -> "B"; case 'В' -> "V"; case 'Г' -> "G"; case 'Д' -> "D"; case 'Е' -> "E";
                case 'Ж' -> "ZH"; case 'З' -> "Z"; case 'И' -> "I"; case 'Й' -> "I"; case 'К' -> "K"; case 'Л' -> "L";
                case 'М' -> "M"; case 'Н' -> "N"; case 'О' -> "O"; case 'П' -> "P"; case 'Р' -> "R"; case 'С' -> "S";
                case 'Т' -> "T"; case 'У' -> "U"; case 'Ф' -> "F"; case 'Х' -> "KH"; case 'Ц' -> "TS"; case 'Ч' -> "CH";
                case 'Ш' -> "SH"; case 'Щ' -> "SHCH"; case 'Ъ' -> "IE"; case 'Ы' -> "Y"; case 'Ь' -> ""; case 'Э' -> "E";
                case 'Ю' -> "IU"; case 'Я' -> "IA";
                default -> String.valueOf(c);
            });
        }
        return b.toString();
    }
}
