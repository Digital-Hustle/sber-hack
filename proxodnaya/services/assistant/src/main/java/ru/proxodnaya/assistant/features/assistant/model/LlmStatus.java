package ru.proxodnaya.assistant.features.assistant.model;

import java.time.Instant;

/**
 * Состояние провайдера по журналу.
 *
 * @param provider       провайдер
 * @param model          модель
 * @param lastCallAt     последний вызов или {@code null}
 * @param lastOk         последний вызов без ошибки
 * @param lastError      ошибка последнего вызова
 * @param callsLastHour  вызовов за час
 * @param avgLatencyMs   средняя задержка за час
 * @param refusedLastHour отказов из-за чисел за час
 */
public record LlmStatus(String provider, String model, Instant lastCallAt, boolean lastOk, String lastError, int callsLastHour,
                        long avgLatencyMs, int refusedLastHour) {
}
