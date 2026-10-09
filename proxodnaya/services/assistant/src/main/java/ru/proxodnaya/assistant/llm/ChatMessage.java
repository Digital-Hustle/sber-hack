package ru.proxodnaya.assistant.llm;

import java.util.List;

/**
 * Сообщение диалога с моделью.
 *
 * @param role       {@code system} | {@code user} | {@code assistant} | {@code tool}
 * @param content    текст или JSON результата инструмента
 * @param toolCalls  вызовы инструментов (у {@code assistant})
 * @param toolCallId к какому вызову относится (у {@code tool})
 */
public record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

    /**
     * Системное сообщение.
     *
     * @param content текст
     * @return сообщение
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, List.of(), null);
    }

    /**
     * Сообщение пользователя.
     *
     * @param content текст
     * @return сообщение
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, List.of(), null);
    }

    /**
     * Ответ модели.
     *
     * @param content   текст
     * @param toolCalls вызовы инструментов
     * @return сообщение
     */
    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls, null);
    }

    /**
     * Результат инструмента.
     *
     * @param toolCallId вызов
     * @param json       результат
     * @return сообщение
     */
    public static ChatMessage tool(String toolCallId, String json) {
        return new ChatMessage("tool", json, List.of(), toolCallId);
    }
}
