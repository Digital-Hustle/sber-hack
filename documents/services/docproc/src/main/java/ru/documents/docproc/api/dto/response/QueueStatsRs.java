package ru.documents.docproc.api.dto.response;

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
 * @param avgSecondsByStage среднее время стадий, с
 * @param p95Seconds        95-й перцентиль полного разбора, с
 */
public record QueueStatsRs(long waiting, long inProgress, long parsed, long needsReview, long unreadable, long waitingModel,
                           long failed, Map<String, Double> avgSecondsByStage, Double p95Seconds) {
}
