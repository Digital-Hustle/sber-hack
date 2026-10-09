package ru.documents.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Матрица «путь × токен», 404 для internal, CORS, лимит тела и частоты (GW-03..05). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.oidc.issuer=https://test/auth/realms/documents",
    "app.cors.allowed-origins=https://docs.example",
    "app.rate-limit.rules[0].id=create",
    "app.rate-limit.rules[0].method=POST",
    "app.rate-limit.rules[0].path=/intake/api/v1/applicant/applications",
    "app.rate-limit.rules[0].limit=2",
    "app.rate-limit.rules[0].window=10m",
    "INTAKE_URL=http://127.0.0.1:1", "DOCPROC_URL=http://127.0.0.1:1", "REVIEW_URL=http://127.0.0.1:1"
})
class GatewaySecurityTest {

    private static final String ISSUER = "https://test/auth/realms/documents";
    private static final RSAKey KEY = generate();
    private static final HttpServer JWKS = startJwks();

    @LocalServerPort
    private int port;

    private WebTestClient web;

    @BeforeEach
    void client() {
        // Реальный сервер, а не mock-обмен: CORS и X-Forwarded-For проверяются как в бою.
        web = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry r) {
        r.add("app.oidc.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/certs");
    }

    @AfterAll
    static void stop() {
        JWKS.stop(0);
    }

    static List<String> operatorPaths() {
        return List.of("/review/api/v1/applications", "/docproc/api/v1/documents/1/result",
                "/intake/api/v1/operator/applications/1", "/review/api/v1/metrics");
    }

    @ParameterizedTest
    @MethodSource("operatorPaths")
    void withoutTokenIs401(String path) {
        web.get().uri(path).exchange().expectStatus().isUnauthorized();
    }

    @ParameterizedTest
    @MethodSource("operatorPaths")
    void garbageExpiredAndForeignTokensAre401(String path) {
        for (String token : List.of("garbage", jwt(ISSUER, Instant.now().minusSeconds(60)),
                jwt("https://evil/realms/documents", Instant.now().plusSeconds(300)))) {
            web.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange().expectStatus().isUnauthorized();
        }
    }

    @ParameterizedTest
    @MethodSource("operatorPaths")
    void validTokenPassesToService(String path) {
        int status = web.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(ISSUER, Instant.now().plusSeconds(300)))
                .exchange().returnResult(String.class).getStatus().value();
        assertThat(status).isNotIn(401, 403, 404);
    }

    @Test
    void internalIs404EvenWithToken() {
        String token = "Bearer " + jwt(ISSUER, Instant.now().plusSeconds(300));
        for (String path : List.of("/intake/internal/intake/files/1", "/internal/x", "/review/INTERNAL/a",
                "/docproc/api/../internal/x")) {
            web.get().uri(path).header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isNotFound();
        }
    }

    @Test
    void unknownPrefixIsDenied() {
        web.get().uri("/admin/api/v1/x").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void corsOnlyOwnOrigin() {
        web.options().uri("/review/api/v1/applications")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isForbidden();
        web.options().uri("/review/api/v1/applications")
                .header(HttpHeaders.ORIGIN, "https://docs.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://docs.example");
    }

    @Test
    void bodyTooLargeForOperatorApiIs413() {
        byte[] big = new byte[2 * 1024 * 1024];
        web.method(HttpMethod.PATCH).uri("/review/api/v1/documents/1/fields")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(ISSUER, Instant.now().plusSeconds(300)))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(big)
                .exchange().expectStatus().isEqualTo(413);
    }

    @Test
    void createApplicationIsRateLimitedPerIp() {
        for (int i = 0; i < 2; i++) {
            int status = create("10.0.0.1").returnResult(String.class).getStatus().value();
            assertThat(status).isNotEqualTo(429);
        }
        create("10.0.0.1").expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
        int other = create("10.0.0.2").returnResult(String.class).getStatus().value();
        assertThat(other).isNotEqualTo(429);
    }

    private WebTestClient.ResponseSpec create(String ip) {
        return web.post().uri("/intake/api/v1/applicant/applications").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"consentPd\":true}").exchange();
    }

    private static String jwt(String issuer, Instant exp) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).subject("operator")
                    .issueTime(Date.from(exp.minusSeconds(600))).expirationTime(Date.from(exp)).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwks() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            s.createContext("/certs", ex -> {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            s.start();
            return s;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
