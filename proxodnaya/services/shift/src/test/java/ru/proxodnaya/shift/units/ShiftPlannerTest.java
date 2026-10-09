package ru.proxodnaya.shift.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.shift.features.shift.command.service.ShiftPlanner;
import ru.proxodnaya.shift.features.shift.model.WeekdayWindow;

class ShiftPlannerTest {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    @Test
    void plan_weekdaysOnly_skipsWeekend() {
        // given: пн 2026-10-05 … вс 2026-10-11, график пн–пт 08:00–20:00
        List<WeekdayWindow> days = List.of(0, 1, 2, 3, 4).stream()
                .map(day -> new WeekdayWindow(day, LocalTime.of(8, 0), LocalTime.of(20, 0))).toList();

        // when
        List<ShiftPlanner.Slot> slots = ShiftPlanner.plan(MSK, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11), days);

        // then
        assertThat(slots).hasSize(5);
        assertThat(slots.getFirst().start()).isEqualTo(Instant.parse("2026-10-05T05:00:00Z"));
        assertThat(slots.getLast().end()).isEqualTo(Instant.parse("2026-10-09T17:00:00Z"));
    }

    @Test
    void slot_nightShift_endsNextDay() {
        // when
        ShiftPlanner.Slot slot = ShiftPlanner.slot(MSK, LocalDate.of(2026, 10, 9), new WeekdayWindow(4, LocalTime.of(20, 0),
                LocalTime.of(8, 0)));

        // then
        assertThat(slot.start()).isEqualTo(Instant.parse("2026-10-09T17:00:00Z"));
        assertThat(slot.end()).isEqualTo(Instant.parse("2026-10-10T05:00:00Z"));
    }

    @Test
    void slot_acrossDstSwitch_realDuration() {
        // given: в Берлине 2026-10-25 часы переводят назад — ночная смена 22:00–06:00 длится 9 ч
        ZoneId berlin = ZoneId.of("Europe/Berlin");

        // when
        ShiftPlanner.Slot slot = ShiftPlanner.slot(berlin, LocalDate.of(2026, 10, 24), new WeekdayWindow(5, LocalTime.of(22, 0),
                LocalTime.of(6, 0)));

        // then
        assertThat(Duration.between(slot.start(), slot.end())).isEqualTo(Duration.ofHours(9));
    }
}
