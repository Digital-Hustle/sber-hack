package ru.documents.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Токен service account (client_credentials) для внутренних вызовов других сервисов.
 * Кэшируется до истечения минус запас.
 */
public class ServiceTokenProvider {

    private static final long SKEW_S = 30;

    private final RestClient http;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(String tokenUri, String clientId, String clientSecret, Clock clock) {
        this.http = RestClient.create();
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    /**
     * Текущий access token.
     *
     * @return bearer-токен
     */
    @SuppressWarnings("unchecked")
    public synchronized String token() {
        if (token != null && clock.instant().isBefore(expiresAt)) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<String, Object> rs = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        if (rs == null || rs.get("access_token") == null) {
            throw new IllegalStateException("no access_token from Keycloak");
        }
        token = (String) rs.get("access_token");
        long ttl = ((Number) rs.getOrDefault("expires_in", 60)).longValue();
        expiresAt = clock.instant().plusSeconds(Math.max(1, ttl - SKEW_S));
        return token;
    }
}
