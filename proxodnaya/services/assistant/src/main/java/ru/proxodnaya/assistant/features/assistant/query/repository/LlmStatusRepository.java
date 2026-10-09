package ru.proxodnaya.assistant.features.assistant.query.repository;

import java.time.Instant;
import java.time.ZoneOffset;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.assistant.jooq.Tables;
import ru.proxodnaya.assistant.jooq.tables.records.LlmRequestsRecord;

/**
 * Чтение журнала вызовов для {@code /assistant/status}.
 */
@Repository
public class LlmStatusRepository {

    private final DSLContext dsl;

    public LlmStatusRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Последний вызов.
     *
     * @return запись или {@code null}
     */
    public LlmRequestsRecord last() {
        var table = Tables.LLM_REQUESTS;
        return dsl.selectFrom(table).orderBy(table.TS.desc(), table.ID.desc()).limit(1).fetchOne();
    }

    /**
     * Сводка с момента.
     *
     * @param since начало
     * @return вызовы, средняя задержка, отказы
     */
    public Window since(Instant since) {
        var table = Tables.LLM_REQUESTS;
        var row = dsl.select(DSL.count(), DSL.avg(table.LATENCY_MS),
                        DSL.countDistinct(table.ASK_ID).filterWhere(table.VALIDATION.eq(DSL.inline("REFUSED"))))
                .from(table)
                .where(table.TS.ge(since.atOffset(ZoneOffset.UTC)))
                .fetchOne();
        if (row == null) {
            return new Window(0, 0, 0);
        }
        return new Window(row.value1(), row.value2() == null ? 0 : row.value2().longValue(), row.value3());
    }

    /**
     * Сводка окна.
     *
     * @param calls        вызовов
     * @param avgLatencyMs средняя задержка
     * @param refused      отказов
     */
    public record Window(int calls, long avgLatencyMs, int refused) {
    }
}
