package ru.documents.common.inbox;

import java.time.Clock;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Идемпотентность потребителя: eventId записывается в processed_events в той же транзакции,
 * что и обработка. Повтор события — no-op.
 */
public class IdempotentConsumer {

    private final JdbcClient jdbc;
    private final Clock clock;

    public IdempotentConsumer(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Отмечает событие обработанным.
     *
     * @param eventId id события
     * @return true, если событие новое и его нужно обработать
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String eventId) {
        return jdbc.sql("insert into processed_events(event_id, processed_at) values (?, ?) on conflict do nothing")
                .params(eventId, java.sql.Timestamp.from(clock.instant()))
                .update() == 1;
    }
}
