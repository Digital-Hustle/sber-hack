package ru.documents.intake.features.applications.model;

/**
 * Результат серверной проверки файла.
 *
 * @param mime   тип по сигнатуре
 * @param pages  страниц
 * @param width  ширина (для изображений)
 * @param height высота
 * @param phash  perceptual hash снимка (null для PDF)
 * @param sha256 хэш содержимого
 */
public record Inspection(String mime, int pages, Integer width, Integer height, Long phash, String sha256) {
}
