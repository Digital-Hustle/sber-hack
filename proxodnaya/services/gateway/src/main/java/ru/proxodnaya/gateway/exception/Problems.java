package ru.proxodnaya.gateway.exception;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Ответы самого gateway в {@code application/problem+json} с {@code code} (docs/API.md).
 */
public final class Problems {

    private Problems() {
    }

    /**
     * Пишет problem+json в ответ.
     *
     * @param exchange обмен
     * @param status   статус
     * @param code     код ошибки
     * @param detail   текст для человека
     * @return завершение записи
     */
    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"type\":\"about:blank\",\"title\":\"" + code + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\",\"instance\":\""
                + escape(exchange.getRequest().getPath().value()) + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
