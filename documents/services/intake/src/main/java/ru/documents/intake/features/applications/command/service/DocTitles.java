package ru.documents.intake.features.applications.command.service;

import java.util.Map;

/**
 * Человеческие названия видов документов (docs/DOC-TYPES.md).
 */
public final class DocTitles {

    private static final Map<String, String> TITLES = Map.of(
            "PASSPORT_FOREIGN", "паспорт иностранного гражданина",
            "PASSPORT_RF", "паспорт РФ",
            "MIGRATION_CARD", "миграционная карта",
            "WORK_PATENT", "патент",
            "ARRIVAL_NOTICE", "уведомление о прибытии",
            "SNILS", "СНИЛС",
            "INN", "ИНН",
            "MEDICAL", "медицинское заключение");

    private DocTitles() {
    }

    /**
     * Название.
     *
     * @param type код вида
     * @return название
     */
    public static String of(String type) {
        return TITLES.getOrDefault(type, "документ");
    }
}
