package ru.documents.intake.api.dto.response;

import java.util.List;

/**
 * Набор документов после анкеты.
 *
 * @param requiredDocuments пункты
 */
public record RequiredDocumentsRs(List<Item> requiredDocuments) {

    /**
     * Пункт.
     *
     * @param type     вид
     * @param title    название
     * @param hint     пояснение
     * @param required обязателен
     */
    public record Item(String type, String title, String hint, boolean required) {
    }
}
