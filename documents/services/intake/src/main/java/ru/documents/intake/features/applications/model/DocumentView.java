package ru.documents.intake.features.applications.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Документ заявки для кабинета (без извлечённых значений полей).
 *
 * @param id           документ
 * @param slotType     пункт, куда положили
 * @param detectedType вид по содержимому
 * @param stage        стадия
 * @param reasonCode   причина
 * @param reasonParams параметры причины
 * @param files        файлы (версии)
 * @param updatedAt    обновлён
 */
public record DocumentView(UUID id, String slotType, String detectedType, String stage, String reasonCode,
                           Map<String, Object> reasonParams, List<FileView> files, Instant updatedAt) {

    /**
     * Файл документа.
     *
     * @param id         файл
     * @param version    версия
     * @param active     текущая
     * @param mime       тип
     * @param pages      страниц
     * @param sizeBytes  размер
     * @param uploadedAt загружен
     */
    public record FileView(UUID id, int version, boolean active, String mime, int pages, long sizeBytes,
                           Instant uploadedAt) {
    }
}
