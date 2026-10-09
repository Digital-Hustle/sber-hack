package ru.proxodnaya.assistant.features.assistant.model;

import java.util.List;

/**
 * Ответ помощника.
 *
 * @param text       текст
 * @param validation проверка чисел
 * @param sources    вызванные инструменты и их данные
 */
public record Answer(String text, Validation validation, List<Source> sources) {
}
