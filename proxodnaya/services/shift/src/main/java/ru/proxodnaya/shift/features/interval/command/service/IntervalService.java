package ru.proxodnaya.shift.features.interval.command.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.common.event.Topics;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.interval.command.model.IntervalPlan;
import ru.proxodnaya.shift.features.interval.command.repository.IntervalRepository;
import ru.proxodnaya.shift.features.interval.command.repository.PassEventRepository;
import ru.proxodnaya.shift.features.interval.model.CloseReason;
import ru.proxodnaya.shift.features.interval.model.Interval;
import ru.proxodnaya.shift.features.interval.model.IntervalFlag;
import ru.proxodnaya.shift.features.interval.model.PassEvent;
import ru.proxodnaya.shift.features.shift.api.ActiveShift;
import ru.proxodnaya.shift.features.shift.api.ShiftLookup;
import ru.proxodnaya.shift.messaging.outbox.Outbox;

/**
 * Интервалы присутствия из проходов (FR-22/23) и автозакрытие по концу смены. События — через outbox
 * {@code shift.interval.v1}: {@code IntervalOpened}, {@code IntervalClosed}.
 */
@Service
public class IntervalService {

    static final int AUTO_CLOSE_BATCH = 200;

    private final PassEventRepository passEvents;
    private final IntervalRepository intervals;
    private final ShiftLookup shifts;
    private final Outbox outbox;
    private final ShiftProperties props;
    private final Clock clock;

    public IntervalService(PassEventRepository passEvents, IntervalRepository intervals, ShiftLookup shifts, Outbox outbox,
                           ShiftProperties props, Clock clock) {
        this.passEvents = passEvents;
        this.intervals = intervals;
        this.shifts = shifts;
        this.outbox = outbox;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Применяет решение о проходе: проекция попытки и, если человек прошёл, интервалы. Повтор события — без эффекта.
     *
     * @param event решение из {@code access.pass.v1}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPass(PassEvent event) {
        if (!passEvents.insert(event) || !event.passed()) {
            return;
        }
        Optional<Interval> open = intervals.lockOpen(event.workerId());
        IntervalPlan plan = IntervalPlan.of(open, event);
        if (plan.closes()) {
            close(open.orElseThrow(), event.ts(), plan.closeReason(), plan.closeFlags(), event.attemptId());
        }
        if (plan.openNew()) {
            open(event);
        }
    }

    /**
     * Закрывает {@code AUTO_SHIFT_END} интервалы без выхода через {@code AUTO_CLOSE_AFTER} после конца смены. Конец
     * интервала — конец смены, с пометкой {@code UNCONFIRMED_EXIT}: это оценка, а не отмеченный выход.
     *
     * @return сколько закрыто
     */
    @Transactional
    public int autoClose() {
        Instant threshold = clock.instant().minus(props.autoCloseAfter());
        List<IntervalRepository.Overdue> overdue = intervals.lockOverdue(threshold, AUTO_CLOSE_BATCH);
        for (IntervalRepository.Overdue row : overdue) {
            Instant end = row.shiftEnd().isBefore(row.interval().enteredAt()) ? row.interval().enteredAt() : row.shiftEnd();
            close(row.interval(), end, CloseReason.AUTO_SHIFT_END, List.of(IntervalFlag.UNCONFIRMED_EXIT), null);
        }
        return overdue.size();
    }

    private void open(PassEvent event) {
        UUID shiftId = shifts.activeShift(event.workerId(), event.ts())
                .filter(ActiveShift::windowOk)
                .map(ActiveShift::shiftId)
                .orElse(null);
        Interval interval = new Interval(UuidCreator.getTimeOrderedEpoch(), event.workerId(), event.zoneId(), shiftId, event.ts(), null,
                null, List.of(), event.simulated());
        intervals.open(interval, event.attemptId());
        Map<String, Object> payload = base(interval);
        outbox.add(Topics.SHIFT_INTERVAL_V1, "IntervalOpened", interval.workerId(), payload);
    }

    private void close(Interval interval, Instant end, CloseReason reason, List<IntervalFlag> flags, UUID attemptId) {
        intervals.close(interval.id(), end, reason, flags, attemptId);
        Map<String, Object> payload = base(interval);
        payload.put("exitedAt", end.toString());
        payload.put("closeReason", reason.name());
        payload.put("flags", flags.stream().map(Enum::name).toList());
        outbox.add(Topics.SHIFT_INTERVAL_V1, "IntervalClosed", interval.workerId(), payload);
    }

    private static Map<String, Object> base(Interval interval) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intervalId", interval.id().toString());
        payload.put("workerId", interval.workerId());
        payload.put("zoneId", interval.zoneId());
        payload.put("shiftId", interval.shiftId() == null ? null : interval.shiftId().toString());
        payload.put("enteredAt", interval.enteredAt().toString());
        payload.put("simulated", interval.simulated());
        return payload;
    }
}
