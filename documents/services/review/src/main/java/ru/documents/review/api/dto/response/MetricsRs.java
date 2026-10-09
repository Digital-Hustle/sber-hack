package ru.documents.review.api.dto.response;

import java.time.Instant;
import java.util.Map;
import ru.documents.review.features.workplace.model.Metrics;

/**
 * Очередь в цифрах.
 *
 * @param applications    заявки по статусам
 * @param documents       документы по стадиям
 * @param waiting         ожидание решения
 * @param decision        время до решения
 * @param fieldsTotal     полей в подтверждённых документах
 * @param fieldsCorrected исправлено оператором
 * @param formulas        как посчитано
 * @param computedAt      когда
 */
public record MetricsRs(Map<String, Long> applications, Map<String, Long> documents, Metrics.Percentiles waiting,
                        Metrics.Percentiles decision, long fieldsTotal, long fieldsCorrected,
                        Map<String, String> formulas, Instant computedAt) {
}
