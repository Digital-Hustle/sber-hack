package ru.proxodnaya.shift.features.shift.model;

import java.time.LocalTime;

/**
 * Время смены в день недели для массового назначения.
 *
 * @param weekday 0..6, 0 — понедельник
 * @param start   начало (пояс площадки)
 * @param end     конец; не позже начала — смена заканчивается на следующий день
 */
public record WeekdayWindow(int weekday, LocalTime start, LocalTime end) {
}
