package ru.proxodnaya.gateway.exception;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import io.netty.channel.ConnectTimeoutException;
import reactor.core.publisher.Mono;

/**
 * Ошибки маршрутизации → problem+json: upstream недоступен — 503 {@code UPSTREAM_UNAVAILABLE}, таймаут — 504.
 * Stack trace наружу не уходит. Порядок — раньше стандартного обработчика Boot (-1).
 */
@Component
@Order(UpstreamErrorHandler.ORDER)
public class UpstreamErrorHandler implements WebExceptionHandler {

    /** Порядок обработчика. */
    static final int ORDER = -2;

    private static final Logger LOG = LoggerFactory.getLogger(UpstreamErrorHandler.class);
    private static final String UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    private static final String DOWN = "Сервис временно недоступен";
    private static final String SLOW = "Сервис не ответил вовремя";
    private static final String SYSTEM = "SYSTEM_ERROR";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        Problem problem = error instanceof ResponseStatusException status ? byStatus(status) : byCause(exchange, error);
        return Problems.write(exchange, problem.status(), problem.code(), problem.detail());
    }

    private static Problem byStatus(ResponseStatusException error) {
        HttpStatus code = HttpStatus.resolve(error.getStatusCode().value());
        HttpStatus status = code == null ? HttpStatus.INTERNAL_SERVER_ERROR : code;
        return switch (status) {
            case NOT_FOUND -> new Problem(status, "NOT_FOUND", "Ресурс не найден");
            case PAYLOAD_TOO_LARGE -> new Problem(status, "PAYLOAD_TOO_LARGE", "Слишком большой запрос");
            case GATEWAY_TIMEOUT -> new Problem(status, UNAVAILABLE, SLOW);
            case SERVICE_UNAVAILABLE -> new Problem(status, UNAVAILABLE, DOWN);
            default -> new Problem(status, status.is4xxClientError() ? "BAD_REQUEST" : SYSTEM, "Запрос не выполнен");
        };
    }

    private static Problem byCause(ServerWebExchange exchange, Throwable error) {
        String path = exchange.getRequest().getPath().value();
        Problem problem;
        if (error instanceof ConnectException || error instanceof ConnectTimeoutException
                || error.getCause() instanceof ConnectException) {
            LOG.warn("upstream недоступен path={}: {}", path, error.getMessage());
            problem = new Problem(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, DOWN);
        } else if (error instanceof TimeoutException) {
            problem = new Problem(HttpStatus.GATEWAY_TIMEOUT, UNAVAILABLE, SLOW);
        } else {
            LOG.error("ошибка gateway path={}", path, error);
            problem = new Problem(HttpStatus.INTERNAL_SERVER_ERROR, SYSTEM, "Внутренняя ошибка");
        }
        return problem;
    }

    private record Problem(HttpStatus status, String code, String detail) {
    }
}
