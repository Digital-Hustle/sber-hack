package ru.proxodnaya.assistant.api.dto.response;

import java.util.List;

/**
 * Ответ помощника.
 *
 * @param text       текст
 * @param validation {@code OK} | {@code REGENERATED} | {@code REFUSED}
 * @param sources    вызванные инструменты
 * @param table      таблица из данных инструмента (не из текста модели) или {@code null}
 * @param data       сырые данные инструментов — только при {@code REFUSED}
 */
public record AskRs(String text, String validation, List<SourceRs> sources, TableRs table, List<ToolDataRs> data) {
}
