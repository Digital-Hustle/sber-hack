package ru.proxodnaya.gateway.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Gateway целиком: настоящий HTTP, upstream-эхо и JWKS на reactor-netty.
 */
@Tag("integration")
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0",
    "MAX_BODY=2KB",
    "gateway.rate-limit.capacity=3",
    "gateway.cors=https://gate.test"
})
class GatewayIntegrationTest {

    private static final String ISSUER = "http://keycloak.test/realms/proxodnaya";
    private static final String DEVICE_ISSUER = "http://people.test/device";
    private static final KeyPair KEYS = rsa();
    private static final KeyPair OTHER = rsa();
    private static final DisposableServer UPSTREAM = upstream();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WORKERS = "/people/api/v1/workers";
    private static final String CODE = "$.code";

    @Autowired
    private WebTestClient client;

    @LocalManagementPort
    private int managementPort;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + UPSTREAM.port();
        registry.add("gateway.issuers[0].issuer", () -> ISSUER);
        registry.add("gateway.issuers[0].jwk-set-uri", () -> base + "/jwks");
        registry.add("gateway.issuers[1].issuer", () -> DEVICE_ISSUER);
        registry.add("gateway.issuers[1].jwk-set-uri", () -> base + "/jwks");
        registry.add("PEOPLE_URL", () -> base);
        registry.add("ACCESS_URL", () -> base);
        registry.add("SHIFT_URL", () -> base);
        registry.add("ASSISTANT_URL", () -> "http://localhost:" + freePort());
        registry.add("OIDC_ISSUER_URI", () -> ISSUER);
    }

    @AfterAll
    static void stop() {
        UPSTREAM.disposeNow();
    }

    @Test
    void withoutToken_401Problem() {
        client.get().uri(WORKERS).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath(CODE).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void validToken_proxiedWithRewriteRequestIdAndNoSpoofedHeaders() throws Exception {
        // when
        byte[] body = client.get().uri(WORKERS + "?q=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ISSUER, KEYS, 300))
                .header("X-User-Id", "admin")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody().returnResult().getResponseBody();

        // then
        JsonNode echo = JSON.readTree(body);
        assertThat(echo.path("path").asText()).isEqualTo("/api/v1/workers?q=1");
        assertThat(echo.path("auth").asText()).startsWith("Bearer ");
        assertThat(echo.path("requestId").asText()).isNotBlank();
        assertThat(echo.path("userId").asText()).isEmpty();
    }

    @Test
    void deviceIssuer_accepted() {
        client.get().uri("/access/api/v1/worker/attempts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(DEVICE_ISSUER, KEYS, 300))
                .exchange().expectStatus().isOk();
    }

    @Test
    void foreignIssuerExpiredOrForeignKey_401() {
        for (String token : new String[] {token("http://evil.test", KEYS, 300), token(ISSUER, KEYS, -120),
            token(ISSUER, OTHER, 300), "garbage"}) {
            client.get().uri(WORKERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Test
    void internalAndActuatorOfServices_404EvenWithToken() {
        String bearer = "Bearer " + token(ISSUER, KEYS, 300);
        for (String path : new String[] {"/people/internal/people/workers/1/access-profile", "/people/api/v1/internal/x",
            "/people/INTERNAL/x", "/access/actuator/env"}) {
            client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearer).exchange()
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath(CODE).isEqualTo("NOT_FOUND");
        }
        // закодированный и «..;» обход режет ещё файрвол Spring (400) — главное, что до сервиса не доходит
        for (String path : new String[] {"/people/%69nternal/people/x", "/people/api/v1/..;/internal"}) {
            client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearer).exchange()
                    .expectStatus().is4xxClientError();
        }
    }

    @Test
    void unknownPath_404() {
        client.get().uri("/billing/api/v1/x").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ISSUER, KEYS, 300))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void activate_publicButRateLimited() {
        for (int attempt = 0; attempt < 3; attempt++) {
            client.post().uri("/people/api/v1/auth/worker/activate").header("X-Forwarded-For", "10.0.0.7")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isOk();
        }
        client.post().uri("/people/api/v1/auth/worker/activate").header("X-Forwarded-For", "10.0.0.7")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists("Retry-After")
                .expectBody().jsonPath(CODE).isEqualTo("RATE_LIMITED");
        client.post().uri("/people/api/v1/auth/worker/activate").header("X-Forwarded-For", "10.0.0.8")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isOk();
    }

    @Test
    void sseTicketPath_publicOtherShiftPathsNot() {
        client.get().uri("/shift/api/v1/stream/situation?ticket=t").exchange().expectStatus().isOk();
        client.get().uri("/shift/api/v1/stream/ticket").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void bodyTooLarge_413() {
        client.post().uri(WORKERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ISSUER, KEYS, 300))
                .header(HttpHeaders.CONTENT_LENGTH, "5000")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("x".repeat(5000)).exchange()
                .expectStatus().isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void upstreamDown_503Problem() {
        client.get().uri("/assistant/api/v1/assistant/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(ISSUER, KEYS, 300))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody().jsonPath(CODE).isEqualTo("UPSTREAM_UNAVAILABLE");
    }

    @Test
    void cors_onlyOwnOrigin() {
        client.options().uri(WORKERS).header(HttpHeaders.ORIGIN, "https://gate.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://gate.test");
        client.options().uri(WORKERS).header(HttpHeaders.ORIGIN, "https://evil.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void managementPort_healthAndPrometheus() {
        WebTestClient management = WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
        management.get().uri("/actuator/health").exchange().expectStatus().isOk();
        management.get().uri("/actuator/prometheus").exchange().expectStatus().isOk();
    }

    private static String token(String issuer, KeyPair keys, long ttlSeconds) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).subject("user-1").audience("people")
                    .issueTime(Date.from(now.minusSeconds(Math.max(0, -ttlSeconds) + 10)))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds))).claim("realm_access", Map.of("roles", new String[] {"ADMIN"}))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new RSASSASigner(keys.getPrivate()));
            return jwt.serialize();
        } catch (com.nimbusds.jose.JOSEException error) {
            throw new IllegalStateException(error);
        }
    }

    private static DisposableServer upstream() {
        String jwks = new JWKSet(new RSAKey.Builder((RSAPublicKey) KEYS.getPublic()).keyID("k1").build()).toString();
        return HttpServer.create().port(0).handle((request, response) -> {
            if (request.uri().startsWith("/jwks")) {
                return response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).sendString(Mono.just(jwks));
            }
            String body = echo(request);
            return request.receive().then().then(response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .sendByteArray(Mono.just(body.getBytes(StandardCharsets.UTF_8))).then());
        }).bindNow();
    }

    private static String echo(reactor.netty.http.server.HttpServerRequest request) {
        Map<String, String> echo = Map.of(
                "path", request.uri(),
                "auth", String.valueOf(request.requestHeaders().get(HttpHeaders.AUTHORIZATION, "")),
                "requestId", String.valueOf(request.requestHeaders().get("X-Request-Id", "")),
                "userId", String.valueOf(request.requestHeaders().get("X-User-Id", "")));
        try {
            return JSON.writeValueAsString(echo);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
