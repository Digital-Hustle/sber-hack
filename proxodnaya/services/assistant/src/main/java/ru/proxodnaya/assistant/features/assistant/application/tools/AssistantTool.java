package ru.proxodnaya.assistant.features.assistant.application.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import ru.proxodnaya.assistant.features.assistant.model.Table;
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

    /**
     * Таблица из результата.
     *
     * @param result данные {@link #call}
     * @return таблица или пусто, если у инструмента её нет
     */
    default Optional<Table> table(JsonNode result) {
        return Optional.empty();
    }

    /**
     * Таблица из массива объектов: по строке на элемент, столбцы — поля.
     *
     * @param tool    инструмент
     * @param items   массив
     * @param columns поля
     * @return таблица или пусто, если массив пуст
     */
    static Optional<Table> rows(String tool, JsonNode items, List<String> columns) {
        List<List<JsonNode>> rows = new ArrayList<>();
        for (JsonNode item : items) {
            rows.add(columns.stream().map(item::path).map(value -> value.isMissingNode() ? null : value).toList());
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(new Table(tool, columns, rows));
    }
}
