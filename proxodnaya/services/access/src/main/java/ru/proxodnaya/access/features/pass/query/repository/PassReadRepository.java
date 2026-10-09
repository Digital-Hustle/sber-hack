package ru.proxodnaya.access.features.pass.query.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.pass.model.Presence;
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
}
