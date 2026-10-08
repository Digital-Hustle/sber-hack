package ru.proxodnaya.people.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Потребители: 3 повтора с паузой 1 с, затем {@code <topic>.DLT}. Ошибка одного события не стопорит партицию.
 */
@Configuration
public class KafkaConfig {

    private static final long BACKOFF_MS = 1000;
    private static final long RETRIES = 3;

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(BACKOFF_MS, RETRIES));
    }
}
