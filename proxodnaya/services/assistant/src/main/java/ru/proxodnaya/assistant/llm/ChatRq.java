package ru.proxodnaya.assistant.llm;

import java.util.List;

/**
 * Запрос к модели.
 *
 * @param messages диалог
 * @param tools    доступные инструменты; пусто — модель должна ответить текстом
 */
public record ChatRq(List<ChatMessage> messages, List<ToolSpec> tools) {
}
