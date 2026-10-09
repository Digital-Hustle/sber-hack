package ru.subsidy.rules.engine.rules;

import java.util.Locale;
import java.util.Map;

/**
 * Нормализация наименований и ФИО для сверки.
 */
final class Names {

    private static final String SPACES = "\\s+";
    private static final String SPACE = " ";
    private static final Map<String, String> FORMS = Map.of(
            "публичное акционерное общество", "пао",
            "общество с ограниченной ответственностью", "ооо",
            "акционерное общество", "ао",
            "индивидуальный предприниматель", "ип");

    private Names() {
    }

    /**
     * «ООО "Ромашка"» и «Общество с ограниченной ответственностью «Ромашка»» → «ооо ромашка».
     *
     * @param name наименование
     * @return нормализованное
     */
    static String organization(String name) {
        String text = name.toLowerCase(Locale.ROOT).replace('ё', 'е');
        for (Map.Entry<String, String> form : FORMS.entrySet()) {
            text = text.replace(form.getKey(), form.getValue());
        }
        return text.replaceAll("[«»\"'“”„.,]", SPACE).replaceAll(SPACES, SPACE).strip();
    }

    /**
     * «Иванов Иван Иванович» и «Иванов И.И.» → «иванов и и».
     *
     * @param person ФИО
     * @return фамилия и инициалы
     */
    static String person(String person) {
        String[] parts = person.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[.,]", SPACE).strip()
                .split(SPACES);
        StringBuilder out = new StringBuilder(parts[0]);
        for (int idx = 1; idx < parts.length; idx++) {
            out.append(' ').append(parts[idx].charAt(0));
        }
        return out.toString();
    }
}
