package ru.documents.common;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import ru.documents.common.exception.BaseExceptionHandler;
import ru.documents.common.inbox.IdempotentConsumer;
import ru.documents.common.outbox.OutboxRelay;
import ru.documents.common.outbox.OutboxWriter;
import ru.documents.common.security.ServiceTokenProvider;

/**
 * Общие бины сервисов: часы, обработчик ошибок, outbox, inbox, токен service account.
 */
@AutoConfiguration
@EnableScheduling
@Import(BaseExceptionHandler.class)
public class CommonAutoConfiguration {

    /** Часы приложения (время только через Clock). */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Запись событий в outbox. */
    @Bean
    public OutboxWriter outboxWriter(JdbcClient jdbc, ObjectMapper json, Clock clock) {
        return new OutboxWriter(jdbc, json, clock);
    }

    /** Идемпотентность потребителей. */
    @Bean
    public IdempotentConsumer idempotentConsumer(JdbcClient jdbc, Clock clock) {
        return new IdempotentConsumer(jdbc, clock);
    }

    /** Relay outbox → Kafka (выключается app.outbox.enabled=false в тестах). */
    @Bean
    @ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelay outboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
                                   Clock clock) {
        return new OutboxRelay(jdbc, kafka, tx, clock);
    }

    /** Токен service account для /internal/** (если задан client id). */
    @Bean
    @ConditionalOnProperty(name = "app.service-account.client-id")
    public ServiceTokenProvider serviceTokenProvider(
            @Value("${app.service-account.token-uri}") String tokenUri,
            @Value("${app.service-account.client-id}") String clientId,
            @Value("${app.service-account.client-secret}") String secret, Clock clock) {
        return new ServiceTokenProvider(tokenUri, clientId, secret, clock);
    }
}
