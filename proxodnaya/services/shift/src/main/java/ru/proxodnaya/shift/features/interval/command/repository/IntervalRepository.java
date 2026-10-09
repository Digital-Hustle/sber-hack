package ru.proxodnaya.shift.features.interval.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.interval.model.CloseReason;
import ru.proxodnaya.shift.features.interval.model.Interval;
import ru.proxodnaya.shift.features.interval.model.IntervalFlag;
import ru.proxodnaya.shift.jooq.Tables;
import ru.proxodnaya.shift.jooq.tables.records.PresenceIntervalsRecord;

/**
 * Запись интервалов присутствия.
 */
@Repository
public class IntervalRepository {

    private static final ru.proxodnaya.shift.jooq.tables.PresenceIntervals INTERVALS = Tables.PRESENCE_INTERVALS;
    private static final ru.proxodnaya.shift.jooq.tables.Shifts SHIFTS = Tables.SHIFTS;

    private final DSLContext dsl;

    public IntervalRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Открытый интервал рабочего, заблокированный до конца транзакции.
     *
     * @param workerId  рабочий
     * @param simulated цепочка симулятора: у неё свои интервалы, реальные не трогает
     * @return интервал
     */
    public Optional<Interval> lockOpen(String workerId, boolean simulated) {
        return dsl.selectFrom(INTERVALS)
                .where(INTERVALS.WORKER_ID.eq(workerId).and(INTERVALS.EXITED_AT.isNull()).and(INTERVALS.SIMULATED.eq(simulated)))
                .forUpdate()
                .fetchOptional(IntervalRepository::interval);
    }

    /**
     * Открытые интервалы со сменой, закончившейся раньше {@code endedBefore}; заблокированы, занятые пропускаются.
     *
     * @param endedBefore граница конца смены
     * @param limit       сколько взять
     * @return интервал и конец его смены
     */
    public List<Overdue> lockOverdue(Instant endedBefore, int limit) {
        return dsl.select(INTERVALS.asterisk(), SHIFTS.ENDS_AT)
                .from(INTERVALS)
                .join(SHIFTS).on(SHIFTS.ID.eq(INTERVALS.SHIFT_ID))
                .where(INTERVALS.EXITED_AT.isNull())
                .and(SHIFTS.ENDS_AT.lt(endedBefore.atOffset(ZoneOffset.UTC)))
                .orderBy(SHIFTS.ENDS_AT)
                .limit(limit)
                .forUpdate().of(INTERVALS)
                .skipLocked()
                .fetch(row -> new Overdue(interval(row.into(INTERVALS)), row.get(SHIFTS.ENDS_AT).toInstant()));
    }

    /**
     * Открывает интервал.
     *
     * @param interval  интервал
     * @param attemptId попытка входа
     */
    public void open(Interval interval, UUID attemptId) {
        dsl.insertInto(INTERVALS)
                .set(INTERVALS.ID, interval.id())
                .set(INTERVALS.WORKER_ID, interval.workerId())
                .set(INTERVALS.ZONE_ID, interval.zoneId())
                .set(INTERVALS.SHIFT_ID, interval.shiftId())
                .set(INTERVALS.ENTERED_AT, interval.enteredAt().atOffset(ZoneOffset.UTC))
                .set(INTERVALS.SIMULATED, interval.simulated())
                .set(INTERVALS.ENTER_ATTEMPT_ID, attemptId)
                .execute();
    }

    /**
     * Закрывает интервал.
     *
     * @param id        интервал
     * @param exitedAt  конец
     * @param reason    причина
     * @param flags     пометки
     * @param attemptId попытка выхода или {@code null}
     */
    public void close(UUID id, Instant exitedAt, CloseReason reason, List<IntervalFlag> flags, UUID attemptId) {
        dsl.update(INTERVALS)
                .set(INTERVALS.EXITED_AT, exitedAt.atOffset(ZoneOffset.UTC))
                .set(INTERVALS.CLOSE_REASON, reason.name())
                .set(INTERVALS.FLAGS, flags.stream().map(Enum::name).toArray(String[]::new))
                .set(INTERVALS.EXIT_ATTEMPT_ID, attemptId)
                .where(INTERVALS.ID.eq(id).and(INTERVALS.EXITED_AT.isNull()))
                .execute();
    }

    static Interval interval(PresenceIntervalsRecord row) {
        return new Interval(row.getId(), row.getWorkerId(), row.getZoneId(), row.getShiftId(), row.getEnteredAt().toInstant(),
                row.getExitedAt() == null ? null : row.getExitedAt().toInstant(),
                row.getCloseReason() == null ? null : CloseReason.valueOf(row.getCloseReason()),
                Arrays.stream(row.getFlags()).map(IntervalFlag::valueOf).toList(), row.getSimulated());
    }

    /**
     * Просроченный интервал.
     *
     * @param interval интервал
     * @param shiftEnd конец его смены
     */
    public record Overdue(Interval interval, Instant shiftEnd) {
    }
}
