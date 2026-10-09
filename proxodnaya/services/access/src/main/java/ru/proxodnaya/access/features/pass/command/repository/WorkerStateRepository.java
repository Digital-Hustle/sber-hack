package ru.proxodnaya.access.features.pass.command.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.pass.model.Presence;
import ru.proxodnaya.access.jooq.Tables;

/**
 * Статус и присутствие рабочего.
 */
@Repository
public class WorkerStateRepository {

    private static final ru.proxodnaya.access.jooq.tables.WorkerState WORKER_STATE = Tables.WORKER_STATE;
    private static final String ZONE_LOCK = "zone:";

    private final DSLContext dsl;

    public WorkerStateRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Статус из people.
     *
     * @param workerId рабочий
     * @param status   статус
     * @param moment       момент события
     */
    public void upsertStatus(String workerId, String status, Instant moment) {
        OffsetDateTime stamp = moment.atOffset(ZoneOffset.UTC);
        dsl.insertInto(WORKER_STATE)
                .set(WORKER_STATE.WORKER_ID, workerId)
                .set(WORKER_STATE.STATUS, status)
                .set(WORKER_STATE.UPDATED_AT, stamp)
                .onConflict(WORKER_STATE.WORKER_ID)
                .doUpdate()
                .set(WORKER_STATE.STATUS, status)
                .set(WORKER_STATE.UPDATED_AT, stamp)
                .execute();
    }

    /**
     * Присутствие под блокировкой строки до конца транзакции ({@code select … for update}). Строки ещё нет — создаётся
     * («снаружи»), чтобы первый вход тоже сериализовался: два киоска не отметят его дважды.
     *
     * @param workerId рабочий
     * @param moment   момент прохода
     * @return присутствие на момент блокировки
     */
    public Presence lockPresence(String workerId, Instant moment) {
        dsl.insertInto(WORKER_STATE)
                .set(WORKER_STATE.WORKER_ID, workerId)
                .set(WORKER_STATE.UPDATED_AT, moment.atOffset(ZoneOffset.UTC))
                .onConflict(WORKER_STATE.WORKER_ID)
                .doNothing()
                .execute();
        return dsl.select(WORKER_STATE.INSIDE, WORKER_STATE.LAST_IN_AT)
                .from(WORKER_STATE)
                .where(WORKER_STATE.WORKER_ID.eq(workerId))
                .forUpdate()
                .fetchSingle(row -> new Presence(Boolean.TRUE.equals(row.value1()),
                        row.value2() == null ? null : row.value2().toInstant()));
    }

    /**
     * Сериализует входы в зону до конца транзакции ({@code pg_advisory_xact_lock}): два киоска не займут последнее
     * место одновременно.
     *
     * @param zoneId зона
     */
    public void lockZone(String zoneId) {
        dsl.execute("select pg_advisory_xact_lock(hashtext({0}))", DSL.val(ZONE_LOCK + zoneId));
    }

    /**
     * Сколько человек внутри зоны: отмечен вход не раньше {@code freshSince}, выхода нет.
     *
     * @param zoneId     зона
     * @param freshSince граница свежести входа
     * @param exceptId   рабочий, которого не считать
     * @return число
     */
    public int insideZone(String zoneId, Instant freshSince, String exceptId) {
        return dsl.fetchCount(WORKER_STATE, WORKER_STATE.INSIDE.isTrue()
                .and(WORKER_STATE.ZONE_ID.eq(zoneId))
                .and(WORKER_STATE.LAST_IN_AT.ge(freshSince.atOffset(ZoneOffset.UTC)))
                .and(WORKER_STATE.WORKER_ID.ne(exceptId)));
    }

    /**
     * Отмечает вход.
     *
     * @param workerId рабочий
     * @param zoneId   зона
     * @param moment   время прохода
     */
    public void markIn(String workerId, String zoneId, Instant moment) {
        OffsetDateTime stamp = moment.atOffset(ZoneOffset.UTC);
        dsl.insertInto(WORKER_STATE)
                .set(WORKER_STATE.WORKER_ID, workerId)
                .set(WORKER_STATE.INSIDE, true)
                .set(WORKER_STATE.ZONE_ID, zoneId)
                .set(WORKER_STATE.LAST_IN_AT, stamp)
                .set(WORKER_STATE.UPDATED_AT, stamp)
                .onConflict(WORKER_STATE.WORKER_ID)
                .doUpdate()
                .set(WORKER_STATE.INSIDE, true)
                .set(WORKER_STATE.ZONE_ID, zoneId)
                .set(WORKER_STATE.LAST_IN_AT, stamp)
                .set(WORKER_STATE.UPDATED_AT, stamp)
                .execute();
    }

    /**
     * Отмечает выход.
     *
     * @param workerId рабочий
     * @param moment       время прохода
     */
    public void markOut(String workerId, Instant moment) {
        OffsetDateTime stamp = moment.atOffset(ZoneOffset.UTC);
        dsl.insertInto(WORKER_STATE)
                .set(WORKER_STATE.WORKER_ID, workerId)
                .set(WORKER_STATE.INSIDE, false)
                .set(WORKER_STATE.LAST_OUT_AT, stamp)
                .set(WORKER_STATE.UPDATED_AT, stamp)
                .onConflict(WORKER_STATE.WORKER_ID)
                .doUpdate()
                .set(WORKER_STATE.INSIDE, false)
                .set(WORKER_STATE.LAST_OUT_AT, stamp)
                .set(WORKER_STATE.UPDATED_AT, stamp)
                .execute();
    }
}
