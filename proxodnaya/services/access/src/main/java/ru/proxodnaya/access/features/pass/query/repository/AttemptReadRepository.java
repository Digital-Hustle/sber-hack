package ru.proxodnaya.access.features.pass.query.repository;

import java.time.ZoneOffset;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.pass.query.model.AttemptCursor;
import ru.proxodnaya.access.features.pass.query.model.AttemptFilter;
import ru.proxodnaya.access.features.pass.query.model.AttemptView;
import ru.proxodnaya.access.jooq.Tables;
import ru.proxodnaya.access.jooq.tables.PassAttempts;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Чтение журнала проходов: фильтры и keyset-страницы по индексам {@code (ts desc)}, {@code (claimed_worker_id, ts desc)}.
 * Колонки перечислены явно: снимок кадра ({@code snapshot}) и hash-цепочка в выдачу не читаются.
 */
@Repository
public class AttemptReadRepository {

    private static final PassAttempts PASS_ATTEMPTS = Tables.PASS_ATTEMPTS;
    private static final Field<String> NOTE = detail("note");
    private static final Field<String> GUARD = detail("guardId");

    private final DSLContext dsl;

    public AttemptReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Страница журнала, новые сверху.
     *
     * @param filter фильтры
     * @param after  курсор последней строки предыдущей страницы ({@code null} — первая страница)
     * @param limit  сколько строк вернуть
     * @return строки
     */
    public List<AttemptView> page(AttemptFilter filter, AttemptCursor after, int limit) {
        Condition where = where(filter);
        if (after != null) {
            where = where.and(DSL.row(PASS_ATTEMPTS.TS, PASS_ATTEMPTS.ID).lt(after.ts().atOffset(ZoneOffset.UTC), after.id()));
        }
        return dsl.select(PASS_ATTEMPTS.ID, PASS_ATTEMPTS.TS, PASS_ATTEMPTS.CHECKPOINT_ID, PASS_ATTEMPTS.ZONE_ID,
                        PASS_ATTEMPTS.DIRECTION, PASS_ATTEMPTS.CLAIMED_WORKER_ID, PASS_ATTEMPTS.DECISION, PASS_ATTEMPTS.REASON_CODE,
                        PASS_ATTEMPTS.SOURCE, PASS_ATTEMPTS.FACE_SCORE, PASS_ATTEMPTS.LATENCY_MS, NOTE, GUARD)
                .from(PASS_ATTEMPTS)
                .where(where)
                .orderBy(PASS_ATTEMPTS.TS.desc(), PASS_ATTEMPTS.ID.desc())
                .limit(limit)
                .fetch(AttemptReadRepository::view);
    }

    /**
     * Сколько строк по фильтру.
     *
     * @param filter фильтры
     * @return количество
     */
    public long count(AttemptFilter filter) {
        return dsl.fetchCount(PASS_ATTEMPTS, where(filter));
    }

    private static Condition where(AttemptFilter filter) {
        return DSL.and(
                eq(PASS_ATTEMPTS.CLAIMED_WORKER_ID, filter.workerId()),
                eq(PASS_ATTEMPTS.CHECKPOINT_ID, filter.checkpointId()),
                eq(PASS_ATTEMPTS.DECISION, filter.decision() == null ? null : filter.decision().name()),
                eq(PASS_ATTEMPTS.REASON_CODE, filter.code() == null ? null : filter.code().name()),
                eq(PASS_ATTEMPTS.SOURCE, filter.source() == null ? null : filter.source().name()),
                eq(PASS_ATTEMPTS.DIRECTION, filter.direction() == null ? null : filter.direction().name()),
                filter.from() == null ? DSL.noCondition() : PASS_ATTEMPTS.TS.ge(filter.from().atOffset(ZoneOffset.UTC)),
                filter.until() == null ? DSL.noCondition() : PASS_ATTEMPTS.TS.lt(filter.until().atOffset(ZoneOffset.UTC)));
    }

    private static Condition eq(Field<String> field, String value) {
        return value == null ? DSL.noCondition() : field.eq(value);
    }

    private static Field<String> detail(String key) {
        return DSL.field("{0} ->> {1}", String.class, PASS_ATTEMPTS.DETAILS, DSL.inline(key));
    }

    private static AttemptView view(Record row) {
        return new AttemptView(row.get(PASS_ATTEMPTS.ID), row.get(PASS_ATTEMPTS.TS).toInstant(), row.get(PASS_ATTEMPTS.CHECKPOINT_ID),
                row.get(PASS_ATTEMPTS.ZONE_ID), Direction.valueOf(row.get(PASS_ATTEMPTS.DIRECTION)),
                row.get(PASS_ATTEMPTS.CLAIMED_WORKER_ID), Decision.valueOf(row.get(PASS_ATTEMPTS.DECISION)),
                ReasonCode.valueOf(row.get(PASS_ATTEMPTS.REASON_CODE)), AttemptSource.valueOf(row.get(PASS_ATTEMPTS.SOURCE)),
                row.get(PASS_ATTEMPTS.FACE_SCORE), row.get(PASS_ATTEMPTS.LATENCY_MS), row.get(NOTE), row.get(GUARD));
    }
}
