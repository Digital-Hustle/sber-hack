package ru.documents.docproc.features.processing.command.repository;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ru.documents.llm.api.LlmCallRecord;

/**
 * Журнал вызовов модели (FR-43).
 */
@Repository
public class LlmCallRepository {

    private final JdbcClient jdbc;

    public LlmCallRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Записать вызов. */
    public void insert(LlmCallRecord c, Instant now) {
        jdbc.sql("""
                insert into llm_calls (document_id, stage, provider, model, latency_ms, tokens_in, tokens_out, schema_valid,
                                       outcome, created_at)
                values (:d, :s, :p, :m, :l, :ti, :to, :sv, :o, :now)""")
                .param("d", c.documentId()).param("s", c.stage()).param("p", c.provider()).param("m", c.model())
                .param("l", c.latencyMs()).param("ti", c.tokensIn()).param("to", c.tokensOut()).param("sv", c.schemaValid())
                .param("o", c.outcome()).param("now", Timestamp.from(now)).update();
    }
}
