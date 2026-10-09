package ru.documents.review.features.workplace.model;

import java.time.Instant;

/**
 * Правка поля оператором (итог = правка ?? модель).
 *
 * @param name      поле
 * @param value     значение
 * @param author    кто
 * @param createdAt когда
 */
public record FieldOverride(String name, String value, String author, Instant createdAt) {
}
