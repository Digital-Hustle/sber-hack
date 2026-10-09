package ru.documents.docproc.features.processing.command.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import ru.documents.docproc.features.processing.model.Job;

/**
 * Очередь заданий в PostgreSQL: захват {@code for update skip locked} с арендой (FR-20).
 */
@Repository
public class JobRepository {

    private static final String COLS = "id, document_id, application_id, step, stage, status, attempts, worker, forced_type, "
            + "vision, created_at, started_at";

    private final JdbcClient jdbc;

    public JobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant ts(ResultSet rs, String c) throws SQLException {
        Timestamp t = rs.getTimestamp(c);
        return t == null ? null : t.toInstant();
    }

    private static Job map(ResultSet rs, int n) throws SQLException {
        return new Job(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                rs.getObject("application_id", UUID.class), rs.getString("step"), rs.getString("stage"),
                rs.getString("status"), rs.getInt("attempts"), rs.getString("worker"), rs.getString("forced_type"),
                rs.getBoolean("vision"), ts(rs, "created_at"), ts(rs, "started_at"));
    }

    /** Новое задание; повтор события ничего не меняет. */
    public boolean insert(UUID id, UUID doc, UUID app, int version, String step, String stage, Instant now) {
        return jdbc.sql("""
                insert into jobs (id, document_id, application_id, file_version, step, stage, status, next_run_at,
                                  created_at, updated_at)
                values (:id, :doc, :app, :v, :step, :stage, 'READY', :now, :now, :now)
                on conflict (document_id) do nothing""")
                .param("id", id).param("doc", doc).param("app", app).param("v", version).param("step", step)
                .param("stage", stage).param("now", Timestamp.from(now)).update() > 0;
    }

    /** Задание документа. */
    public Optional<Job> byDocument(UUID doc) {
        return jdbc.sql("select " + COLS + " from jobs where document_id = :d").param("d", doc).query(JobRepository::map)
                .optional();
    }

    /** Захватить следующее готовое задание или задание с истёкшей арендой. */
    public Optional<Job> claim(String worker, Instant now, Instant leaseUntil) {
        return jdbc.sql("""
                update jobs set status = 'RUNNING', worker = :w, lease_until = :lease, updated_at = :now,
                       started_at = coalesce(started_at, :now)
                where id = (select id from jobs
                            where (status = 'READY' and next_run_at <= :now)
                               or (status = 'RUNNING' and lease_until < :now)
                            order by next_run_at limit 1 for update skip locked)
                returning\s""" + COLS)
                .param("w", worker).param("lease", Timestamp.from(leaseUntil)).param("now", Timestamp.from(now))
                .query(JobRepository::map).optional();
    }

    /** Продлить аренду, если задание всё ещё наше. */
    public boolean extend(UUID id, String worker, Instant leaseUntil) {
        return jdbc.sql("update jobs set lease_until = :l where id = :id and worker = :w and status = 'RUNNING'")
                .param("l", Timestamp.from(leaseUntil)).param("id", id).param("w", worker).update() > 0;
    }

    /** Блокировка своей строки перед записью результата шага. */
    public boolean lockOwned(UUID id, String worker) {
        return !jdbc.sql("select id from jobs where id = :id and worker = :w and status = 'RUNNING' for update")
                .param("id", id).param("w", worker).query().listOfRows().isEmpty();
    }

    /** Видимая стадия. */
    public void setStage(UUID id, String stage, Instant now) {
        jdbc.sql("update jobs set stage = :s, updated_at = :now where id = :id")
                .param("s", stage).param("now", Timestamp.from(now)).param("id", id).update();
    }

    /** Шаг выполнен — следующий шаг (аренда остаётся у воркера). */
    public void advance(UUID id, String step, Instant now) {
        jdbc.sql("update jobs set step = :s, attempts = 0, last_error_code = null, updated_at = :now where id = :id")
                .param("s", step).param("now", Timestamp.from(now)).param("id", id).update();
    }

    /** Включить vision-путь. */
    public void setVision(UUID id, boolean vision) {
        jdbc.sql("update jobs set vision = :v where id = :id").param("v", vision).param("id", id).update();
    }

    /** Завершено. */
    public void finish(UUID id, String stage, Instant now) {
        jdbc.sql("""
                update jobs set status = 'DONE', stage = :s, lease_until = null, worker = null, finished_at = :now,
                       updated_at = :now where id = :id""")
                .param("s", stage).param("now", Timestamp.from(now)).param("id", id).update();
    }

    /** Отложить: вернуть в очередь к моменту {@code at}; попытка считается, если {@code countAttempt}. */
    public void postpone(UUID id, Instant at, boolean countAttempt, String error, Instant now) {
        jdbc.sql("""
                update jobs set status = 'READY', worker = null, lease_until = null, next_run_at = :at,
                       attempts = attempts + :inc, last_error_code = :e, updated_at = :now where id = :id""")
                .param("at", Timestamp.from(at)).param("inc", countAttempt ? 1 : 0).param("e", error)
                .param("now", Timestamp.from(now)).param("id", id).update();
    }

    /** Начать заново с шага (новые файлы, переразбор, смена типа). */
    public void restart(UUID doc, String step, String stage, String forcedType, Integer version, Instant now) {
        jdbc.sql("""
                update jobs set status = 'READY', step = :step, stage = :stage, attempts = 0, worker = null,
                       lease_until = null, next_run_at = :now, forced_type = :ft, vision = false, last_error_code = null,
                       file_version = coalesce(:v, file_version), started_at = null, finished_at = null, updated_at = :now
                where document_id = :doc""")
                .param("step", step).param("stage", stage).param("ft", forcedType).param("v", version)
                .param("now", Timestamp.from(now)).param("doc", doc).update();
    }

    /** Документ удалён. */
    public void cancel(UUID doc, Instant now) {
        jdbc.sql("update jobs set status = 'CANCELLED', worker = null, lease_until = null, updated_at = :now "
                + "where document_id = :doc").param("now", Timestamp.from(now)).param("doc", doc).update();
    }
}
