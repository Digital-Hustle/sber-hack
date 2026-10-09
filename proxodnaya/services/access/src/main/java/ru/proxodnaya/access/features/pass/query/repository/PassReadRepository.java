package ru.proxodnaya.access.features.pass.query.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SortOrder;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.pass.model.Presence;
import ru.proxodnaya.access.features.pass.query.model.AccessStats;
import ru.proxodnaya.access.jooq.tables.records.PassAttemptsRecord;
import ru.proxodnaya.access.jooq.Tables;

/**
 * Чтение состояния для проверок до челленджа.
 */
@Repository
public class PassReadRepository {

    private static final ru.proxodnaya.access.jooq.tables.PassAttempts PASS_ATTEMPTS = Tables.PASS_ATTEMPTS;
    private static final ru.proxodnaya.access.jooq.tables.WorkerState WORKER_STATE = Tables.WORKER_STATE;

    private static final List<String> FACE_FAILURES = List.of("FACE_MISMATCH", "LIVENESS_FAILED");

    private final DSLContext dsl;

    public PassReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Статус из проекции people.
     *
     * @param workerId рабочий
     * @return статус или пусто
     */
    public Optional<String> status(String workerId) {
        return dsl.select(WORKER_STATE.STATUS)
                .from(WORKER_STATE)
                .where(WORKER_STATE.WORKER_ID.eq(workerId))
                .fetchOptional(WORKER_STATE.STATUS);
    }

    /**
     * Присутствие.
     *
     * @param workerId рабочий
     * @return присутствие или пусто
     */
    public Optional<Presence> presence(String workerId) {
        return dsl.select(WORKER_STATE.INSIDE, WORKER_STATE.LAST_IN_AT)
                .from(WORKER_STATE)
                .where(WORKER_STATE.WORKER_ID.eq(workerId))
                .fetchOptional(row -> new Presence(Boolean.TRUE.equals(row.value1()),
                        row.value2() == null ? null : row.value2().toInstant()));
    }

    /**
     * Последний пропуск рабочего.
     *
     * @param workerId рабочий
     * @return время или пусто
     */
    public Optional<Instant> lastAllowAt(String workerId) {
        return dsl.select(PASS_ATTEMPTS.TS)
                .from(PASS_ATTEMPTS)
                .where(PASS_ATTEMPTS.CLAIMED_WORKER_ID.eq(workerId).and(PASS_ATTEMPTS.DECISION.eq("ALLOW")))
                .orderBy(PASS_ATTEMPTS.TS.desc())
                .limit(1)
                .fetchOptional(PASS_ATTEMPTS.TS)
                .map(OffsetDateTime::toInstant);
    }

    /**
     * Неудачные сверки лица с момента {@code since}.
     *
     * @param workerId рабочий
     * @param since    начало окна
     * @return количество
     */
    public int faceFailuresSince(String workerId, Instant since) {
        return dsl.fetchCount(PASS_ATTEMPTS, PASS_ATTEMPTS.CLAIMED_WORKER_ID.eq(workerId)
                .and(PASS_ATTEMPTS.TS.gt(since.atOffset(ZoneOffset.UTC)))
                .and(PASS_ATTEMPTS.REASON_CODE.in(FACE_FAILURES)));
    }

    /**
     * Список попыток по фильтру (журнал, FR-10/31). Курсорная пагинация по {@code ts desc, id desc}.
     *
     * @param workerId   фильтр по рабочему (null — все)
     * @param checkpoint фильтр по проходной (null — все)
     * @param decision   фильтр по решению (null — все)
     * @param code       фильтр по коду причины (null — все)
     * @param source     фильтр по источнику (null — все)
     * @param from       от (включительно, null — без ограничения снизу)
     * @param to         до (включительно, null — без ограничения сверху)
     * @param cursorTs   курсор: ts предыдущей страницы (null — первая страница)
     * @param limit      размер страницы
     * @return список записей
     */
    public List<PassAttemptsRecord> findAttempts(
            String workerId, String checkpoint, String decision, String code, String source,
            Instant from, Instant to, Instant cursorTs, int limit) {
        Condition c = DSL.noCondition();
        if (workerId != null && !workerId.isBlank()) {
            c = c.and(PASS_ATTEMPTS.CLAIMED_WORKER_ID.eq(workerId));
        }
        if (checkpoint != null && !checkpoint.isBlank()) {
            c = c.and(PASS_ATTEMPTS.CHECKPOINT_ID.eq(checkpoint));
        }
        if (decision != null && !decision.isBlank()) {
            c = c.and(PASS_ATTEMPTS.DECISION.eq(decision));
        }
        if (code != null && !code.isBlank()) {
            c = c.and(PASS_ATTEMPTS.REASON_CODE.eq(code));
        }
        if (source != null && !source.isBlank()) {
            c = c.and(PASS_ATTEMPTS.SOURCE.eq(source));
        }
        if (from != null) {
            c = c.and(PASS_ATTEMPTS.TS.greaterOrEqual(from.atOffset(ZoneOffset.UTC)));
        }
        if (to != null) {
            c = c.and(PASS_ATTEMPTS.TS.lessOrEqual(to.atOffset(ZoneOffset.UTC)));
        }
        if (cursorTs != null) {
            c = c.and(PASS_ATTEMPTS.TS.lessThan(cursorTs.atOffset(ZoneOffset.UTC)));
        }
        return dsl.selectFrom(PASS_ATTEMPTS)
                .where(c)
                .orderBy(PASS_ATTEMPTS.TS.sort(SortOrder.DESC), PASS_ATTEMPTS.ID.sort(SortOrder.DESC))
                .limit(Math.min(limit, 500))
                .fetchInto(PassAttemptsRecord.class);
    }

    /**
     * Считает количество попыток по фильтру (для total в ответе).
     */
    public long countAttempts(
            String workerId, String checkpoint, String decision, String code, String source,
            Instant from, Instant to) {
        Condition c = DSL.noCondition();
        if (workerId != null && !workerId.isBlank()) {
            c = c.and(PASS_ATTEMPTS.CLAIMED_WORKER_ID.eq(workerId));
        }
        if (checkpoint != null && !checkpoint.isBlank()) {
            c = c.and(PASS_ATTEMPTS.CHECKPOINT_ID.eq(checkpoint));
        }
        if (decision != null && !decision.isBlank()) {
            c = c.and(PASS_ATTEMPTS.DECISION.eq(decision));
        }
        if (code != null && !code.isBlank()) {
            c = c.and(PASS_ATTEMPTS.REASON_CODE.eq(code));
        }
        if (source != null && !source.isBlank()) {
            c = c.and(PASS_ATTEMPTS.SOURCE.eq(source));
        }
        if (from != null) {
            c = c.and(PASS_ATTEMPTS.TS.greaterOrEqual(from.atOffset(ZoneOffset.UTC)));
        }
        if (to != null) {
            c = c.and(PASS_ATTEMPTS.TS.lessOrEqual(to.atOffset(ZoneOffset.UTC)));
        }
        return dsl.fetchCount(PASS_ATTEMPTS, c);
    }

    private static final java.util.Set<String> SYSTEM_CODES = java.util.Set.of(
            "FACE_NOT_FOUND", "FACE_LOW_QUALITY", "CHALLENGE_EXPIRED", "SYSTEM_ERROR", "QR_MISSING");

    /**
     * Статистика для assistant (/internal/access/stats, FR-42).
     *
     * @param from начало
     * @param to   конец
     * @return агрегаты
     */
    public AccessStats stats(Instant from, Instant to) {
        Condition range = PASS_ATTEMPTS.TS.greaterOrEqual(from.atOffset(ZoneOffset.UTC))
                .and(PASS_ATTEMPTS.TS.lessOrEqual(to.atOffset(ZoneOffset.UTC)));

        long total = dsl.fetchCount(PASS_ATTEMPTS, range);
        long allow = dsl.fetchCount(PASS_ATTEMPTS, range.and(PASS_ATTEMPTS.DECISION.in("ALLOW", "MANUAL")));
        long deny = dsl.fetchCount(PASS_ATTEMPTS, range.and(PASS_ATTEMPTS.DECISION.eq("DENY")));

        Condition denyRange = range.and(PASS_ATTEMPTS.DECISION.eq("DENY"));
        long denySystem = dsl.fetchCount(PASS_ATTEMPTS, denyRange.and(PASS_ATTEMPTS.REASON_CODE.in(SYSTEM_CODES)));

        var refusals = dsl.select(PASS_ATTEMPTS.REASON_CODE, org.jooq.impl.DSL.count())
                .from(PASS_ATTEMPTS)
                .where(denyRange)
                .groupBy(PASS_ATTEMPTS.REASON_CODE)
                .fetch()
                .map(r -> new AccessStats.RefusalStat(r.value1(), r.value2(), SYSTEM_CODES.contains(r.value1())));

        var load = new java.util.ArrayList<AccessStats.HourLoad>();
        for (int h = 0; h < 24; h++) {
            long in = dsl.fetchCount(PASS_ATTEMPTS, range
                    .and(PASS_ATTEMPTS.DECISION.in("ALLOW", "MANUAL"))
                    .and(PASS_ATTEMPTS.DIRECTION.eq("IN"))
                    .and(org.jooq.impl.DSL.extract(org.jooq.impl.DSL.field("ts"), org.jooq.DatePart.HOUR).eq(h)));
            long out = dsl.fetchCount(PASS_ATTEMPTS, range
                    .and(PASS_ATTEMPTS.DECISION.in("ALLOW", "MANUAL"))
                    .and(PASS_ATTEMPTS.DIRECTION.eq("OUT"))
                    .and(org.jooq.impl.DSL.extract(org.jooq.impl.DSL.field("ts"), org.jooq.DatePart.HOUR).eq(h)));
            load.add(new AccessStats.HourLoad(h, in, out));
        }

        return new AccessStats(from, to, total, allow, deny, denySystem, refusals, load);
    }
}
