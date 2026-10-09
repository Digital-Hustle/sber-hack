package ru.proxodnaya.shift.features.shift.command.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import ru.proxodnaya.shift.features.shift.model.WeekdayWindow;

/**
 * Разворачивает недельный график в смены по датам.
 *
 * <p>Время — в поясе площадки (переход на летнее время учитывается); конец не позже начала — смена через полночь,
 * заканчивается на следующий день.</p>
 */
public final class ShiftPlanner {

    private ShiftPlanner() {
    }

    /**
     * Смены за даты {@code [first, last]} включительно.
     *
     * @param zone    пояс площадки
     * @param first   первая дата
     * @param last    последняя дата
     * @param windows время по дням недели (день недели не повторяется)
     * @return пары начало/конец по порядку
     */
    public static List<Slot> plan(ZoneId zone, LocalDate first, LocalDate last, List<WeekdayWindow> windows) {
        Map<DayOfWeek, WeekdayWindow> byDay = windows.stream()
                .collect(Collectors.toMap(window -> DayOfWeek.of(window.weekday() + 1), Function.identity()));
        List<Slot> slots = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            WeekdayWindow window = byDay.get(day.getDayOfWeek());
            if (window != null) {
                slots.add(slot(zone, day, window));
            }
        }
        return slots;
    }

    /**
     * Одна смена по дате и времени.
     *
     * @param zone   пояс площадки
     * @param day    дата начала
     * @param window время
     * @return начало и конец
     */
    public static Slot slot(ZoneId zone, LocalDate day, WeekdayWindow window) {
        ZonedDateTime start = day.atTime(window.start()).atZone(zone);
        LocalDate endDay = window.end().isAfter(window.start()) ? day : day.plusDays(1);
        ZonedDateTime end = endDay.atTime(window.end()).atZone(zone);
        return new Slot(start.toInstant(), end.toInstant());
    }

    /**
     * Начало и конец смены.
     *
     * @param start начало
     * @param end   конец
     */
    public record Slot(Instant start, Instant end) {
    }
}
