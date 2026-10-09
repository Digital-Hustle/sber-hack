package ru.proxodnaya.shift.api.dto.response;

/**
 * Итог массового назначения.
 *
 * @param created создано
 * @param skipped пропущено (пересеклись с уже назначенными)
 */
public record BulkShiftRs(int created, int skipped) {
}
