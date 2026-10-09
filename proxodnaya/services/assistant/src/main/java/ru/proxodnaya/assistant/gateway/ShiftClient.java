package ru.proxodnaya.assistant.gateway;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Чтение аналитики shift токеном того, кто спросил: помощник видит ровно то, что доступно пользователю (RBAC shift).
 */
@Component
public class ShiftClient {

    private final RestClient http;

    public ShiftClient(@Qualifier("shift") RestClient http) {
        this.http = http;
    }

    /**
     * GET к API shift.
     *
     * @param path   путь от {@code /api/v1}
     * @param params параметры запроса (пустые пропускаются)
     * @return JSON ответа
     * @throws ShiftUnavailableException если shift ответил ошибкой или недоступен
     */
    public JsonNode get(String path, Map<String, String> params) {
        try {
            return http.get()
                    .uri(builder -> {
                        builder.path("/api/v1" + path);
                        params.forEach((name, value) -> {
                            if (value != null && !value.isBlank()) {
                                builder.queryParam(name, value);
                            }
                        });
                        return builder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException error) {
            throw new ShiftUnavailableException("shift ответил " + error.getStatusCode().value(), error);
        } catch (RestClientException error) {
            throw new ShiftUnavailableException("shift недоступен", error);
        }
    }

    private static String token() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw new ShiftUnavailableException("нет токена пользователя", null);
    }
}
