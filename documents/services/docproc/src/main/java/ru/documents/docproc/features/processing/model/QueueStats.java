package ru.documents.docproc.features.processing.model;

import java.util.Map;

/**
 * Состояние очереди (B-06).
 *
 * @param waiting           в очереди
 * @param inProgress        в работе
 * @param parsed            разобрано
 * @param needsReview       на проверку
 * @param unreadable        нечитаемо
 * @param waitingModel      ждут модель
 * @param failed            ошибка
 * @param avgSecondsByStage среднее время стадий за сутки
 * @param p95Seconds        95-й перцентиль полного разбора за сутки
 */
public record QueueStats(long waiting, long inProgress, long parsed, long needsReview, long unreadable, long waitingModel,
                         long failed, Map<String, Double> avgSecondsByStage, Double p95Seconds) {
}
