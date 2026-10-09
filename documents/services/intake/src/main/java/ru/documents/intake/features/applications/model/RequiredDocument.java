package ru.documents.intake.features.applications.model;

/**
 * Пункт набора документов.
 *
 * @param type     вид документа
 * @param title    название
 * @param hint     пояснение
 * @param required обязателен
 * @param state    MISSING | PROCESSING | DONE | PROBLEM
 */
public record RequiredDocument(String type, String title, String hint, boolean required, String state) {
}
