package ru.proxodnaya.people.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sun.net.httpserver.HttpServer;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.config.properties.SharedMailProperties;
import ru.proxodnaya.people.features.staff.gateway.SharedStaffMailer;

/**
 * Внутренний контракт общего почтового клиента (MAIL-01).
 */
class SharedStaffMailerTest {

    @Test
    void loginCode_sendsServiceAndCode_withInternalAuthorization() throws IOException {
        // given
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> payload = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        server.createContext("/internal/mail", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(HttpStatus.NO_CONTENT.value(), -1);
            exchange.close();
        });
        server.start();
        try {
            // when
            mailer(server).loginCode("operator@example.com", "123456", 10);
            // then
            assertThat(auth.get()).isEqualTo("Bearer test-internal-token");
            assertThat(payload.get()).contains("\"service\":\"proxodnaya\"", "\"kind\":\"login\"", "\"code\":\"123456\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingSmtp_isServiceUnavailable_notSuccessfulSend() throws IOException {
        // given
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/mail", exchange -> {
            exchange.sendResponseHeaders(HttpStatus.SERVICE_UNAVAILABLE.value(), -1);
            exchange.close();
        });
        server.start();
        try {
            // when / then
            assertThatThrownBy(() -> mailer(server).loginCode("operator@example.com", "123456", 10))
                    .isInstanceOfSatisfying(DomainException.class,
                            error -> assertThat(error.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        } finally {
            server.stop(0);
        }
    }

    private SharedStaffMailer mailer(HttpServer server) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return new SharedStaffMailer(new SharedMailProperties(true, url, "test-internal-token", Duration.ofSeconds(1)));
    }
}
