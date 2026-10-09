package ru.proxodnaya.shift.features.shift.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Смена к созданию.
 *
 * @param workerId   рабочий
 * @param start      начало
 * @param end        конец
 * @param templateId шаблон или {@code null}
 */
public record ShiftDraft(String workerId, Instant start, Instant end, UUID templateId) {
}
