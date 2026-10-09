package ru.proxodnaya.assistant.features.assistant.model;

import java.util.List;

/**
 * Ответ помощника.
 *
 * @param text       текст
 * @param validation проверка чисел
 * @param sources    вызванные инструменты и их данные
 * @param table      таблица из данных последнего инструмента с таблицей или {@code null}
 */
public record Answer(String text, Validation validation, List<Source> sources, Table table) {
}
