package ru.documents.intake.features.applications.model;

/**
 * Содержимое файла для выдачи.
 *
 * @param bytes байты
 * @param mime  тип
 * @param pages всего страниц
 */
public record FileContent(byte[] bytes, String mime, int pages) {
}
