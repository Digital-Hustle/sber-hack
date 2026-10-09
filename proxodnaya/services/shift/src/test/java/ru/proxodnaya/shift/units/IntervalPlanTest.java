package ru.proxodnaya.shift.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.shift.features.interval.command.model.IntervalPlan;
import ru.proxodnaya.shift.features.interval.model.CloseReason;
import ru.proxodnaya.shift.features.interval.model.Interval;
import ru.proxodnaya.shift.features.interval.model.IntervalFlag;
import ru.proxodnaya.shift.features.interval.model.PassEvent;

class IntervalPlanTest {

    private static final Instant ENTERED = Instant.parse("2026-10-09T20:00:00Z");
    private static final Optional<Interval> OPEN_MAIN = Optional.of(new Interval(UUID.randomUUID(), "w1", "zone-main", null, ENTERED,
            null, null, List.of(), false));

    @Test
    void entry_withoutOpen_opens() {
        // when
        IntervalPlan plan = IntervalPlan.of(Optional.empty(), pass("IN", "zone-main", ENTERED));

        // then
        assertThat(plan.closes()).isFalse();
        assertThat(plan.openNew()).isTrue();
    }

    @Test
    void exit_afterNightEntry_closesWithExit() {
        // when: вход в 23:00 МСК, выход в 07:00 следующего дня
        IntervalPlan plan = IntervalPlan.of(OPEN_MAIN, pass("OUT", "zone-main", ENTERED.plusSeconds(8 * 3600)));

        // then
        assertThat(plan.closeReason()).isEqualTo(CloseReason.EXIT);
        assertThat(plan.closeFlags()).isEmpty();
        assertThat(plan.openNew()).isFalse();
    }

    @Test
    void entry_otherZone_zoneChangeAndOpen() {
        // when
        IntervalPlan plan = IntervalPlan.of(OPEN_MAIN, pass("IN", "zone-b", ENTERED.plusSeconds(600)));

        // then
        assertThat(plan.closeReason()).isEqualTo(CloseReason.ZONE_CHANGE);
        assertThat(plan.openNew()).isTrue();
    }

    @Test
    void entry_sameZoneWithoutExit_reentryUnconfirmed() {
        // when
        IntervalPlan plan = IntervalPlan.of(OPEN_MAIN, pass("IN", "zone-main", ENTERED.plusSeconds(3600)));

        // then
        assertThat(plan.closeReason()).isEqualTo(CloseReason.REENTRY);
        assertThat(plan.closeFlags()).containsExactly(IntervalFlag.UNCONFIRMED_EXIT);
        assertThat(plan.openNew()).isTrue();
    }

    @Test
    void exit_withoutOpen_nothing() {
        // when
        IntervalPlan plan = IntervalPlan.of(Optional.empty(), pass("OUT", "zone-main", ENTERED));

        // then
        assertThat(plan.closes()).isFalse();
        assertThat(plan.openNew()).isFalse();
    }

    @Test
    void event_olderThanOpen_ignored() {
        // when
        IntervalPlan plan = IntervalPlan.of(OPEN_MAIN, pass("OUT", "zone-main", ENTERED.minusSeconds(1)));

        // then
        assertThat(plan.closes()).isFalse();
        assertThat(plan.openNew()).isFalse();
    }

    @Test
    void passed_denyOrWithoutWorker_false() {
        // then
        assertThat(new PassEvent(UUID.randomUUID(), "w1", "cp", "z", "IN", "DENY", "NO_SHIFT", "KIOSK", ENTERED).passed()).isFalse();
        assertThat(new PassEvent(UUID.randomUUID(), null, "cp", "z", "IN", "ALLOW", "OK", "KIOSK", ENTERED).passed()).isFalse();
        assertThat(new PassEvent(UUID.randomUUID(), "w1", "cp", "z", "IN", "MANUAL", "MANUAL_GUARD", "MANUAL", ENTERED).passed()).isTrue();
    }

    private static PassEvent pass(String direction, String zone, Instant moment) {
        return new PassEvent(UUID.randomUUID(), "w1", "cp_main", zone, direction, "ALLOW", "OK", "KIOSK", moment);
    }
}
