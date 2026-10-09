package ru.documents.review.features.workplace.command.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.documents.review.features.workplace.persistence.RowMappers;

/**
 * Одноразовые билеты SSE (в БД хранится только хеш).
 */
@Repository
public class TicketRepository {

    private final JdbcClient jdbc;

    public TicketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Выдать. */
    public void insert(String hash, String username, Instant expires, Instant now) {
        jdbc.sql("delete from stream_tickets where expires_at < ?").param(RowMappers.ts(now)).update();
        jdbc.sql("insert into stream_tickets(ticket_hash, username, expires_at) values (?, ?, ?)")
                .params(hash, username, RowMappers.ts(expires)).update();
    }

    /** Погасить. */
    public Optional<String> consume(String hash, Instant now) {
        return jdbc.sql("delete from stream_tickets where ticket_hash = ? and expires_at > ? returning username")
                .params(hash, RowMappers.ts(now)).query(String.class).optional();
    }
}
