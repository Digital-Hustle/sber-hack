package ru.documents.common.outbox;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Перекладывает события из outbox в Kafka: пачкой, по порядку, с блокировкой SKIP LOCKED.
 * Kafka недоступна — события остаются в таблице и уйдут позже (ничего не теряется).
 */
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;
    private static final long SEND_TIMEOUT_S = 10;

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tx = tx;
        this.clock = clock;
    }

    /** Один проход relay. */
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:300}")
    public void relay() {
        Integer sent;
        do {
            sent = tx.execute(s -> sendBatch());
        } while (sent != null && sent == BATCH);
    }

    private int sendBatch() {
        List<Row> rows = jdbc.sql("""
                select id, topic, msg_key, payload::text as payload from outbox
                 where published_at is null order by created_at, id limit ? for update skip locked""")
                .param(BATCH)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                        rs.getString("msg_key"), rs.getString("payload")))
                .list();
        int ok = 0;
        for (Row r : rows) {
            try {
                kafka.send(r.topic(), r.key(), r.payload()).get(SEND_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException e) {
                LOG.warn("outbox send failed topic={} id={}: {}", r.topic(), r.id(), e.getMessage());
                break;
            }
            jdbc.sql("update outbox set published_at = ? where id = ?")
                    .params(java.sql.Timestamp.from(clock.instant()), r.id()).update();
            ok++;
        }
        return ok;
    }

    private record Row(UUID id, String topic, String key, String payload) {
    }
}
