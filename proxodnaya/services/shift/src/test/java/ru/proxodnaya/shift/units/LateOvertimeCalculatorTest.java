package ru.proxodnaya.shift.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.shift.features.analytics.query.model.PresenceFact;
import ru.proxodnaya.shift.features.analytics.query.model.ShiftOutcome;
import ru.proxodnaya.shift.features.analytics.query.model.ShiftOutcome.Arrival;
import ru.proxodnaya.shift.features.analytics.query.model.ShiftSlot;
import ru.proxodnaya.shift.features.analytics.query.service.LateOvertimeCalculator;

class LateOvertimeCalculatorTest {

    private static final Instant START = Instant.parse("2026-10-05T05:00:00Z");
    private static final Instant END = Instant.parse("2026-10-05T14:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");
    private static final ShiftSlot SHIFT = new ShiftSlot(UUID.randomUUID(), "w1", START, END);
    private static final LateOvertimeCalculator CALC = new LateOvertimeCalculator(Duration.ofMinutes(60), Duration.ofMinutes(5),
            Duration.ofMinutes(15));

    @Test
    void evaluate_lateAndOvertime() {
        // when
        ShiftOutcome outcome = CALC.evaluate(SHIFT, List.of(fact("05:20", "09:00", false), fact("10:00", "14:40", false)), NOW);

        // then
        assertThat(outcome.arrival()).isEqualTo(Arrival.LATE);
        assertThat(outcome.lateMinutes()).isEqualTo(20);
        assertThat(outcome.overtimeMinutes()).isEqualTo(40);
        assertThat(outcome.overtime()).isTrue();
        assertThat(outcome.incomplete()).isFalse();
    }

    @Test
    void evaluate_withinGraceAndBelowThreshold_onTimeNoOvertime() {
        // when
        ShiftOutcome outcome = CALC.evaluate(SHIFT, List.of(fact("04:30", "14:10", false)), NOW);

        // then
        assertThat(outcome.arrival()).isEqualTo(Arrival.ON_TIME);
        assertThat(outcome.lateMinutes()).isZero();
        assertThat(outcome.overtimeMinutes()).isEqualTo(10);
        assertThat(outcome.overtime()).isFalse();
    }

    @Test
    void evaluate_unconfirmedExit_noOvertimeIncomplete() {
        // when
        ShiftOutcome outcome = CALC.evaluate(SHIFT, List.of(fact("05:03", "14:00", true)), NOW);

        // then
        assertThat(outcome.arrival()).isEqualTo(Arrival.ON_TIME);
        assertThat(outcome.overtime()).isFalse();
        assertThat(outcome.incomplete()).isTrue();
    }

    @Test
    void evaluate_noEntry_absentOrNotYet_entryOutsideWindowIgnored() {
        // given: вход за 2 ч до начала и выход за час до начала — не эта смена
        List<PresenceFact> before = List.of(fact("03:00", "04:00", false));

        // when / then
        assertThat(CALC.evaluate(SHIFT, before, NOW).arrival()).isEqualTo(Arrival.ABSENT);
        assertThat(CALC.evaluate(SHIFT, List.of(), START.plusSeconds(600)).arrival()).isEqualTo(Arrival.NOT_YET);
    }

    private static PresenceFact fact(String entered, String exited, boolean unconfirmed) {
        return new PresenceFact("w1", Instant.parse("2026-10-05T" + entered + ":00Z"), Instant.parse("2026-10-05T" + exited + ":00Z"),
                false, unconfirmed);
    }
}
