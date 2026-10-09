package ru.proxodnaya.shift.messaging.consumer;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Выполняет обработку события ровно один раз: отметка и действие в одной транзакции.
 */
@Component
public class IdempotentConsumer {

    private final ProcessedEventRepository repository;
    private final Clock clock;

    public IdempotentConsumer(ProcessedEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Обрабатывает событие, если оно ещё не обработано.
     *
     * @param eventId событие
     * @param action  обработка
     */
    @Transactional
    public void once(UUID eventId, Runnable action) {
        if (repository.markProcessed(eventId, clock.instant())) {
            action.run();
        }
    }
}
