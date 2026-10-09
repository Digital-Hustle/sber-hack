package ru.documents.review.features.workplace.command.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.documents.review.features.workplace.persistence.RowMappers;

/**
 * История заявки: только INSERT (UPDATE/DELETE запрещены триггером).
 */
@Repository
public class HistoryRepository {

    private final JdbcClient jdbc;

    public HistoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Обезличить историю удалённой заявки (NFR-10): из правок, решений и запросов убираются значения полей и тексты
     * оператора, запись остаётся с пометкой redacted. Триггер разрешает UPDATE только ровно такого вида.
     */
    public void redact(UUID app) {
        jdbc.sql("""
                update application_history
                   set details = (details - array['from', 'to', 'modelValue', 'comment', 'items']) || '{"redacted": true}'
                 where application_id = ? and action in ('FIELD_CORRECTED', 'DECIDED', 'RESUBMIT_REQUESTED')
                   and details -> 'redacted' is null""").param(app).update();
    }

    /** Добавить запись. */
    public void add(UUID id, UUID app, UUID doc, String actor, String source, String action, String detailsJson,
                    Instant now) {
        jdbc.sql("""
                insert into application_history(id, application_id, document_id, actor, source, action, details,
                    created_at) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)""")
                .params(id, app, doc, actor, source, action, detailsJson, RowMappers.ts(now)).update();
    }
}
