package ru.proxodnaya.assistant.api.dto.response;

import java.time.Instant;

/**
 * Состояние помощника.
 *
 * @param provider        провайдер LLM
 * @param model           модель
 * @param lastCallAt      последний вызов модели
 * @param lastOk          последний вызов без ошибки
 * @param lastError       ошибка последнего вызова
 * @param callsLastHour   вызовов модели за час
 * @param avgLatencyMs    средняя задержка за час
 * @param refusedLastHour вопросов с отказом из-за чисел за час
 */
public record StatusRs(String provider, String model, Instant lastCallAt, boolean lastOk, String lastError, int callsLastHour,
                       long avgLatencyMs, int refusedLastHour) {
}
