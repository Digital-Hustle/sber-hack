package ru.documents.review.features.workplace.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Замечание по комплекту: CROSS_MISMATCH, MISSING_DOCUMENT.
 *
 * @param code        код (текст и подсказка — API.md)
 * @param field       поле
 * @param documentIds документы, между которыми расхождение
 * @param params      параметры текста
 */
public record Finding(String code, String field, List<UUID> documentIds, Map<String, String> params) {
}
