package ru.documents.review.features.workplace.rules;

import java.util.Map;

/**
 * Названия видов документов для отображения, пока результата разбора ещё нет (реестр — docproc `doc_types`).
 */
public final class DocTypes {

    private static final Map<String, String> NAMES = Map.of(
            "PASSPORT_FOREIGN", "Паспорт иностранного гражданина",
            "PASSPORT_RF", "Паспорт гражданина РФ",
            "MIGRATION_CARD", "Миграционная карта",
            "WORK_PATENT", "Патент на работу",
            "ARRIVAL_NOTICE", "Уведомление о прибытии",
            "SNILS", "СНИЛС",
            "INN", "ИНН",
            "MEDICAL", "Медицинское заключение",
            "UNKNOWN", "Вид не определён");

    private DocTypes() {
    }

    /**
     * Название вида.
     *
     * @param code код
     * @return название или сам код
     */
    public static String name(String code) {
        return code == null ? null : NAMES.getOrDefault(code, code);
    }
}
