package ru.proxodnaya.shift.features.alert.command.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.shift.features.alert.model.OverdueShift;
import ru.proxodnaya.shift.jooq.Tables;

/**
 * Напоминания о невыходе (B-04).
 */
@Repository
public class ReminderRepository {

    private static final ru.proxodnaya.shift.jooq.tables.ExitReminders REMINDERS = Tables.EXIT_REMINDERS;
    private static final ru.proxodnaya.shift.jooq.tables.PresenceIntervals INTERVALS = Tables.PRESENCE_INTERVALS;
    private static final ru.proxodnaya.shift.jooq.tables.Shifts SHIFTS = Tables.SHIFTS;

    private final DSLContext dsl;

    public ReminderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Смены, кончившиеся в окне, без напоминания, у рабочего которых открыт интервал, начатый до конца смены.
     *
     * @param endedAfter  конец смены позже
     * @param endedBefore конец смены не позже
     * @return смены с входом открытого интервала (открытый интервал у рабочего один)
     */
    public List<OverdueShift> overdue(Instant endedAfter, Instant endedBefore) {
        return dsl.select(SHIFTS.ID, SHIFTS.WORKER_ID, SHIFTS.STARTS_AT, SHIFTS.ENDS_AT, INTERVALS.ENTERED_AT)
                .from(SHIFTS)
                .join(INTERVALS).on(INTERVALS.WORKER_ID.eq(SHIFTS.WORKER_ID))
                .where(SHIFTS.ENDS_AT.gt(utc(endedAfter)))
                .and(SHIFTS.ENDS_AT.le(utc(endedBefore)))
                .and(INTERVALS.EXITED_AT.isNull())
                .and(INTERVALS.SIMULATED.isFalse())
                .and(INTERVALS.ENTERED_AT.lt(SHIFTS.ENDS_AT))
                .andNotExists(dsl.selectOne().from(REMINDERS).where(REMINDERS.SHIFT_ID.eq(SHIFTS.ID)))
                .orderBy(SHIFTS.ENDS_AT, SHIFTS.ID)
                .fetch(row -> new OverdueShift(row.value1(), row.value2(), row.value3().toInstant(), row.value4().toInstant(),
                        row.value5().toInstant()));
    }

    /**
     * Отмечает напоминание; повторное — пропускается.
     *
     * @param shiftId  смена
     * @param workerId рабочий
     * @param moment   когда
     * @return записано ли впервые
     */
    public boolean markSent(UUID shiftId, String workerId, Instant moment) {
        return dsl.insertInto(REMINDERS)
                .set(REMINDERS.SHIFT_ID, shiftId)
                .set(REMINDERS.WORKER_ID, workerId)
                .set(REMINDERS.SENT_AT, utc(moment))
                .onConflictDoNothing()
                .execute() > 0;
    }

    private static OffsetDateTime utc(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC);
    }
}
