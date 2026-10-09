package ru.documents.review.features.workplace.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.documents.common.exception.DomainException;
import ru.documents.common.security.ServiceTokenProvider;

/**
 * Шлюз intake: сводка заявки (анкета, файлы документов, запросы, контакты) по service account svc-review.
 */
@Component
public class IntakeGateway {

    private final RestClient http;
    private final ServiceTokenProvider tokens;

    public IntakeGateway(@Qualifier("intakeRestClient") RestClient http, ServiceTokenProvider tokens) {
        this.http = http;
        this.tokens = tokens;
    }

    /**
     * Сводка заявки.
     *
     * @param applicationId заявка
     * @return сводка или пусто, если intake её не знает
     */
    public Optional<JsonNode> summary(UUID applicationId) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/intake/applications/{id}/summary", applicationId)
                    .header("Authorization", "Bearer " + tokens.token()).retrieve().body(JsonNode.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new DomainException("INTAKE_UNAVAILABLE", 503, "Сервис приёма документов недоступен",
                    "Повторите через минуту");
        }
    }
}
