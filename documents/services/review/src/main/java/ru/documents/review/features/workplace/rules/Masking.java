package ru.documents.review.features.workplace.rules;

import java.util.Set;

/**
 * Маскирование ПДн (B-07): номера документов — до «показать» с записью в аудит, ФИО и контакты — для аудитора.
 */
public final class Masking {

    /** Поля-номера: показываются только последние 4 символа. */
    public static final Set<String> NUMBER_FIELDS = Set.of("documentNumber", "number", "series", "passportNumber",
            "snils", "inn");

    private static final int VISIBLE = 4;
    private static final char DOT = '•';

    private Masking() {
    }

    /**
     * Номер ли это документа.
     *
     * @param field имя поля
     * @return true — маскировать
     */
    public static boolean isNumber(String field) {
        return NUMBER_FIELDS.contains(field);
    }

    /**
     * Номер: видны последние 4 буквы/цифры, разделители сохраняются.
     *
     * @param v значение
     * @return маска
     */
    public static String number(String v) {
        if (v == null) {
            return null;
        }
        long alnum = v.chars().filter(Character::isLetterOrDigit).count();
        long hide = Math.max(alnum - VISIBLE, alnum > 2 ? 1 : alnum);
        StringBuilder b = new StringBuilder();
        for (char c : v.toCharArray()) {
            if (Character.isLetterOrDigit(c) && hide > 0) {
                b.append(DOT);
                hide--;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * ФИО и прочий текст: первая буква каждого слова.
     *
     * @param v значение
     * @return маска
     */
    public static String text(String v) {
        if (v == null) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        for (String w : v.trim().split("\\s+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (!b.isEmpty()) {
                b.append(' ');
            }
            b.append(w.charAt(0)).append("•••");
        }
        return b.toString();
    }

    /**
     * Любое значение поля для аудитора: номера — как номера, остальное — как текст.
     *
     * @param field имя поля
     * @param v     значение
     * @return маска
     */
    public static String any(String field, String v) {
        if (v == null) {
            return null;
        }
        return isNumber(field) || v.chars().anyMatch(Character::isDigit) ? number(v) : text(v);
    }

    /**
     * Телефон или e-mail: начало и конец.
     *
     * @param v контакт
     * @return маска
     */
    public static String contact(String v) {
        if (v == null || v.length() < VISIBLE) {
            return v;
        }
        int at = v.indexOf('@');
        if (at > 0) {
            return v.charAt(0) + "•••" + v.substring(at);
        }
        return v.substring(0, 2) + "•••" + v.substring(v.length() - 2);
    }
}
