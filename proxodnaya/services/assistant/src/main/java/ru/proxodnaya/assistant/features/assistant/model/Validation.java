package ru.proxodnaya.assistant.features.assistant.model;

/**
 * Итог проверки чисел ответа (FR-43).
 */
public enum Validation {
    /** Все числа — из данных инструментов. */
    OK,
    /** С первого раза были лишние числа, вторая попытка прошла. */
    REGENERATED,
    /** Модель дважды придумала числа — отказ с сырыми данными. */
    REFUSED
}
