package ru.proxodnaya.shift.features.shift.model;

/**
 * Итог массового назначения.
 *
 * @param created создано смен
 * @param skipped пропущено: пересеклись с уже назначенными
 */
public record BulkResult(int created, int skipped) {
}
