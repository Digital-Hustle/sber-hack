package ru.documents.docproc.features.processing.command.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Выходы стадий (для продолжения после рестарта) и журнал переходов.
 */
@Repository
public class OutputRepository {

    private final JdbcClient jdbc;

    public OutputRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Сохранить выход стадии (перезапись при повторе). */
    public void save(UUID doc, String stage, String json, Instant now) {
        jdbc.sql("""
                insert into stage_outputs (document_id, stage, payload, created_at) values (:d, :s, cast(:p as jsonb), :now)
                on conflict (document_id, stage) do update set payload = excluded.payload, created_at = excluded.created_at""")
                .param("d", doc).param("s", stage).param("p", json).param("now", Timestamp.from(now)).update();
    }

    /** Все выходы документа. */
    public Map<String, String> load(UUID doc) {
        Map<String, String> out = new java.util.HashMap<>();
        jdbc.sql("select stage, payload::text as p from stage_outputs where document_id = :d").param("d", doc)
                .query((rs, n) -> out.put(rs.getString("stage"), rs.getString("p"))).list();
        return out;
    }

    /** Удалить выходы, кроме перечисленных стадий. */
    public void deleteExcept(UUID doc, java.util.List<String> keep) {
        if (keep.isEmpty()) {
            jdbc.sql("delete from stage_outputs where document_id = :d").param("d", doc).update();
            return;
        }
        jdbc.sql("delete from stage_outputs where document_id = :d and stage not in (:k)").param("d", doc)
                .param("k", keep).update();
    }

    /** Переход стадии. */
    public void event(UUID doc, UUID app, String stage, String reason, Long durationMs, Instant now) {
        jdbc.sql("""
                insert into stage_events (document_id, application_id, stage, reason_code, duration_ms, at)
                values (:d, :a, :s, :r, :ms, :now)""")
                .param("d", doc).param("a", app).param("s", stage).param("r", reason).param("ms", durationMs)
                .param("now", Timestamp.from(now)).update();
    }
}
