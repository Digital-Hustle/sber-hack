package ru.documents.review.features.staff.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Почта сотрудника: нормализация, простая проверка формы, маска для ответа. */
public final class Emails {

    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]{1,64}@[a-z0-9.-]{1,190}\\.[a-z]{2,24}$");
    private static final int SHOW = 2;

    private Emails() {
    }

    /** Без пробелов, в нижнем регистре. */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Похоже на адрес. */
    public static boolean valid(String email) {
        return EMAIL.matcher(email).matches();
    }

    /**
     * Скрыть середину имени: {@code ivanov@x.ru → iv••••@x.ru}.
     *
     * @param email нормализованная почта
     * @return маска
     */
    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String name = email.substring(0, at);
        String shown = name.substring(0, Math.min(SHOW, name.length()));
        return shown + "•".repeat(Math.max(1, name.length() - shown.length())) + email.substring(at);
    }
}
