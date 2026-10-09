package ru.proxodnaya.assistant.api.dto.response;

import java.util.List;

/**
 * Ответ помощника.
 *
 * @param text       текст
 * @param validation {@code OK} | {@code REGENERATED} | {@code REFUSED}
 * @param sources    вызванные инструменты
 * @param data       сырые данные инструментов — только при {@code REFUSED}
 */
public record AskRs(String text, String validation, List<SourceRs> sources, List<ToolDataRs> data) {
}
