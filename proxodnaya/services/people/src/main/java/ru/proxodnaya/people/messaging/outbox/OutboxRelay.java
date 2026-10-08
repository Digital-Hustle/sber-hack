package ru.proxodnaya.people.messaging.outbox;

import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отправляет события из outbox в Kafka (at-least-once): отправили → {@code published_at}.
 */
@Component
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;
    private static final long SEND_TIMEOUT_SECONDS = 2;
    private static final long DELAY_MS = 200;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.clock = clock;
    }

    /**
     * Отправляет пачку. Ошибка Kafka — пачка остаётся в outbox до следующего запуска.
     */
    @Scheduled(fixedDelay = DELAY_MS)
    @Transactional
    public void relay() {
        for (OutboxRepository.OutboxMessage message : repository.lockUnpublished(BATCH)) {
            if (!send(message)) {
                break;
            }
            repository.markPublished(message.id(), clock.instant());
        }
    }

    private boolean send(OutboxRepository.OutboxMessage message) {
        boolean sent = false;
        try {
            kafka.send(message.topic(), message.key(), message.payload()).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            sent = true;
        } catch (ExecutionException | TimeoutException error) {
            LOG.warn("outbox: не отправлено событие {} в {}: {}", message.id(), message.topic(), error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        return sent;
    }
}
