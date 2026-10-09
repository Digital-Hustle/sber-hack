package ru.proxodnaya.shift.api.dto.response;

import java.util.List;

/**
 * Шаблоны смен.
 *
 * @param items шаблоны по названию
 */
public record ShiftTemplatesRs(List<ShiftTemplateRs> items) {
}
