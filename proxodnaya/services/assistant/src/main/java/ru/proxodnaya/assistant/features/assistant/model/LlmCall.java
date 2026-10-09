package ru.proxodnaya.assistant.features.assistant.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись журнала вызова модели.
 *
 * @param askId        вопрос
 * @param ts           когда
 * @param provider     провайдер
 * @param model        модель
 * @param latencyMs    задержка
 * @param tokensIn     токенов в запросе
 * @param tokensOut    токенов в ответе
 * @param toolCalls    сколько инструментов запросила модель
 * @param validation   итог проверки (у последнего вызова)
 * @param error        ошибка провайдера
 * @param questionHash SHA-256 вопроса
 */
public record LlmCall(UUID askId, Instant ts, String provider, String model, long latencyMs, Integer tokensIn, Integer tokensOut,
                      int toolCalls, Validation validation, String error, String questionHash) {
}
