package ru.documents.docproc.features.processing.query.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Чтение результатов и статистики очереди.
 */
@Repository
public class ResultReadRepository {

    private final JdbcClient jdbc;

    public ResultReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Текущий результат документа (строка как map). */
    public Optional<Map<String, Object>> current(UUID doc) {
        return jdbc.sql("""
                select id, document_id, application_id, stage, type, type_confidence, evidence::text as evidence,
                       reasons::text as reasons, quality::text as quality, mrz::text as mrz, pages::text as pages, model,
                       prompt_version, schema_version, durations::text as durations, created_at
                from parse_results where document_id = :d and current""").param("d", doc).query().listOfRows().stream()
                .findFirst();
    }

    /** Поля результата по порядку. */
    public List<Map<String, Object>> fields(UUID result) {
        return jdbc.sql("""
                select name, title, value, raw, page, bbox::text as bbox, confidence, checks::text as checks, reason
                from fields where result_id = :r order by sort""").param("r", result).query().listOfRows();
    }

    /** Стадия и статус задания. */
    public Optional<Map<String, Object>> job(UUID doc) {
        return jdbc.sql("select application_id, stage, status, attempts, last_error_code, updated_at from jobs "
                + "where document_id = :d").param("d", doc).query().listOfRows().stream().findFirst();
    }

    /** Сколько заданий по стадиям среди незавершённых и завершённых. */
    public Map<String, Long> countsByStage() {
        Map<String, Long> out = new java.util.HashMap<>();
        jdbc.sql("""
                select case when status = 'RUNNING' then 'RUNNING' else stage end as k, count(*) as c from jobs
                where status <> 'CANCELLED' group by 1""")
                .query((rs, n) -> out.put(rs.getString("k"), rs.getLong("c"))).list();
        return out;
    }

    /** Средняя длительность стадий (мс) по результатам с момента {@code since}. */
    public Map<String, Double> avgStageMs(Instant since) {
        Map<String, Double> out = new java.util.LinkedHashMap<>();
        jdbc.sql("""
                select key, avg(value::numeric) as ms from parse_results, jsonb_each_text(coalesce(durations, '{}'))
                where created_at >= :s group by key""").param("s", Timestamp.from(since))
                .query((rs, n) -> out.put(rs.getString("key"), rs.getDouble("ms"))).list();
        return out;
    }

    /** 95-й перцентиль полного времени разбора, секунд. */
    public Double p95Seconds(Instant since) {
        return jdbc.sql("""
                select percentile_cont(0.95) within group (order by extract(epoch from finished_at - started_at)) as p
                from jobs where finished_at >= :s and started_at is not null""").param("s", Timestamp.from(since))
                .query(Double.class).optional().orElse(null);
    }
}
