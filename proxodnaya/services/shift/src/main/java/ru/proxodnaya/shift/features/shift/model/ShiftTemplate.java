package ru.proxodnaya.shift.features.shift.model;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Шаблон смены: время в поясе площадки; конец не позже начала — смена через полночь.
 *
 * @param id    id
 * @param name  название («День», «Ночь»)
 * @param start начало
 * @param end   конец
 */
public record ShiftTemplate(UUID id, String name, LocalTime start, LocalTime end) {
}
