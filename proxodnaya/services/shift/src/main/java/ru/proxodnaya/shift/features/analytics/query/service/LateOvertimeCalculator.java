package ru.proxodnaya.shift.features.analytics.query.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import ru.proxodnaya.shift.features.analytics.query.model.PresenceFact;
import ru.proxodnaya.shift.features.analytics.query.model.ShiftOutcome;
import ru.proxodnaya.shift.features.analytics.query.model.ShiftOutcome.Arrival;
import ru.proxodnaya.shift.features.analytics.query.model.ShiftSlot;

/**
 * Смена против интервалов: опоздание по первому входу в окне смены, переработка по последнему выходу.
 *
 * @param earlyEntry        насколько раньше начала смены вход ещё относится к ней
 * @param lateGrace         допуск опоздания
 * @param overtimeThreshold порог переработки
 */
public record LateOvertimeCalculator(Duration earlyEntry, Duration lateGrace, Duration overtimeThreshold) {

    private static final long SECONDS_IN_MINUTE = 60;

    /**
     * Считает одну смену.
     *
     * @param shift смена (уже началась)
     * @param facts интервалы этого рабочего, по входу
     * @param now   текущий момент
     * @return итог смены
     */
    public ShiftOutcome evaluate(ShiftSlot shift, List<PresenceFact> facts, Instant now) {
        Instant windowStart = shift.start().minus(earlyEntry);
        List<PresenceFact> inWindow = facts.stream()
                .filter(fact -> fact.enteredAt().isBefore(shift.end()) && fact.end().isAfter(windowStart))
                .toList();
        if (inWindow.isEmpty()) {
            Arrival arrival = shift.end().isAfter(now) ? Arrival.NOT_YET : Arrival.ABSENT;
            return new ShiftOutcome(shift.id(), shift.workerId(), shift.start(), shift.end(), arrival, null, null, 0, 0, false, false);
        }
        Instant firstEntry = inWindow.getFirst().enteredAt();
        long lateSeconds = Math.max(0, Duration.between(shift.start(), firstEntry).toSeconds());
        Arrival arrival = lateSeconds > lateGrace.toSeconds() ? Arrival.LATE : Arrival.ON_TIME;
        PresenceFact last = inWindow.stream().max(Comparator.comparing(PresenceFact::end)).orElseThrow();
        boolean incomplete = inWindow.stream().anyMatch(PresenceFact::unconfirmed);
        long overtimeSeconds = incomplete ? 0 : Math.max(0, Duration.between(shift.end(), last.end()).toSeconds());
        boolean overtime = overtimeSeconds >= overtimeThreshold.toSeconds() && overtimeSeconds > 0;
        return new ShiftOutcome(shift.id(), shift.workerId(), shift.start(), shift.end(), arrival, firstEntry, last.end(),
                arrival == Arrival.LATE ? lateSeconds / SECONDS_IN_MINUTE : 0, overtimeSeconds / SECONDS_IN_MINUTE, overtime, incomplete);
    }
}
