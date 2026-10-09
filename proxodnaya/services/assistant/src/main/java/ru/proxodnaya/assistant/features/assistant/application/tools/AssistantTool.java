package ru.proxodnaya.assistant.features.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;

import ru.proxodnaya.assistant.llm.ToolSpec;

/**
 * Инструмент помощника: имя и JSON-схема для модели, вызов read-only API.
 */
public interface AssistantTool {

    /**
     * Описание для модели.
     *
     * @return имя, описание, схема аргументов
     */
    ToolSpec spec();

    /**
     * Вызов.
     *
     * @param args аргументы от модели
     * @return данные; производные числа (часы, проценты) считает сервер
     * @throws ToolArgumentException если аргументы не по схеме
     */
    JsonNode call(JsonNode args);
}
