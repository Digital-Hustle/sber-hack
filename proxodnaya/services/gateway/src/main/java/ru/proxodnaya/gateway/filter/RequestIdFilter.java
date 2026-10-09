package ru.proxodnaya.gateway.filter;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * {@code X-Request-Id}: свой или новый, уходит в upstream и обратно клиенту (GW-07).
 * Чужие доверенные заголовки пользователя срезаются — пользователь только из JWT.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestIdFilter implements WebFilter {

    /** Заголовок идентификатора запроса. */
    public static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    /** Префиксы заголовков «доверенного пользователя» — от клиента не принимаем ни один. */
    private static final String[] SPOOFED_PREFIXES = {"x-user-", "x-forwarded-user", "x-auth-request-"};

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.set(HEADER, requestId);
            headers.keySet().removeIf(RequestIdFilter::spoofed);
        }).build();
        exchange.getResponse().getHeaders().set(HEADER, requestId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static boolean spoofed(String header) {
        String name = header.toLowerCase(Locale.ROOT);
        for (String prefix : SPOOFED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
