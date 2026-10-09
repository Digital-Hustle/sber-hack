package ru.proxodnaya.assistant.llm;

import java.util.List;

/**
 * Ответ модели.
 *
 * @param content   текст (без рассуждений {@code <think>})
 * @param toolCalls вызовы инструментов
 * @param tokensIn  токенов в запросе, если провайдер сообщил
 * @param tokensOut токенов в ответе
 */
public record ChatRs(String content, List<ToolCall> toolCalls, Integer tokensIn, Integer tokensOut) {
}
