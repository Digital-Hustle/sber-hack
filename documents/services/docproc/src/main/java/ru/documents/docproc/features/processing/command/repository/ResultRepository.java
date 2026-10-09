package ru.documents.docproc.features.processing.command.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Итоги разбора: parse_results (история, текущий — один) и fields.
 */
@Repository
public class ResultRepository {

    private final JdbcClient jdbc;

    public ResultRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Снять признак «текущий» с прошлых результатов документа. */
    public void retire(UUID doc) {
        jdbc.sql("update parse_results set current = false where document_id = :d and current").param("d", doc).update();
    }

    /** Удалить все результаты документа вместе с полями (NFR-10: документ или заявка удалены). */
    public void deleteAll(UUID doc) {
        jdbc.sql("delete from parse_results where document_id = :d").param("d", doc).update();
    }

    /** Новый текущий результат. */
    public void insert(UUID id, UUID doc, UUID app, String stage, String type, Double typeConf, String evidence,
                       String reasons, String quality, String mrz, String pages, String model, String prompt,
                       Integer schemaVersion, String durations, Instant now) {
        jdbc.sql("""
                insert into parse_results (id, document_id, application_id, current, stage, type, type_confidence, evidence,
                       reasons, quality, mrz, pages, model, prompt_version, schema_version, durations, created_at)
                values (:id, :doc, :app, true, :stage, :type, :tc, cast(:ev as jsonb), cast(:rs as jsonb), cast(:q as jsonb),
                       cast(:mrz as jsonb), cast(:pages as jsonb), :model, :pv, :sv, cast(:dur as jsonb), :now)""")
                .param("id", id).param("doc", doc).param("app", app).param("stage", stage).param("type", type)
                .param("tc", typeConf).param("ev", evidence).param("rs", reasons).param("q", quality).param("mrz", mrz)
                .param("pages", pages).param("model", model).param("pv", prompt).param("sv", schemaVersion)
                .param("dur", durations).param("now", Timestamp.from(now)).update();
    }

    /** Поле результата. */
    public void field(UUID result, String name, String title, int sort, String value, String raw, Integer page,
                      String bbox, Double confidence, String checks, String reason) {
        jdbc.sql("""
                insert into fields (result_id, name, title, sort, value, raw, page, bbox, confidence, checks, reason)
                values (:r, :n, :t, :s, :v, :raw, :p, cast(:b as jsonb), :c, cast(:ch as jsonb), :reason)""")
                .param("r", result).param("n", name).param("t", title).param("s", sort).param("v", value)
                .param("raw", raw).param("p", page).param("b", bbox).param("c", confidence).param("ch", checks)
                .param("reason", reason).update();
    }
}
