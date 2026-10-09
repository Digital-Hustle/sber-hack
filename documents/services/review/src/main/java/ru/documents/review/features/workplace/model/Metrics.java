package ru.documents.review.features.workplace.model;

import java.time.Instant;
import java.util.Map;

/**
 * Очередь в цифрах (B-06): только из таблиц проекции, пустая БД → нули и null.
 *
 * @param applications   заявки по статусам
 * @param documents      документы по стадиям
 * @param waiting        ожидание решения у отправленных и не решённых: p50/p95, секунды
 * @param decision       от отправки до решения: p50/p95, секунды
 * @param fieldsTotal    полей в подтверждённых документах
 * @param fieldsCorrected из них исправлено оператором
 * @param formulas       как посчитано
 * @param computedAt     когда посчитано
 */
public record Metrics(Map<String, Long> applications, Map<String, Long> documents, Percentiles waiting,
                      Percentiles decision, long fieldsTotal, long fieldsCorrected, Map<String, String> formulas,
                      Instant computedAt) {

    /**
     * Перцентили.
     *
     * @param count выборка
     * @param p50   медиана
     * @param p95   95-й
     */
    public record Percentiles(long count, Double p50, Double p95) {
    }
}
