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

    /** Добавить запись. */
    public void add(UUID id, UUID app, UUID doc, String actor, String source, String action, String detailsJson,
                    Instant now) {
        jdbc.sql("""
                insert into application_history(id, application_id, document_id, actor, source, action, details,
                    created_at) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)""")
                .params(id, app, doc, actor, source, action, detailsJson, RowMappers.ts(now)).update();
    }
}
