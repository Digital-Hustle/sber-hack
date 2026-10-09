package ru.documents.gateway.limit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Лимиты создания заявок, загрузок и входа по коду (FR-06): превышение → 429 problem+json и Retry-After.
 * IP клиента — первый адрес X-Forwarded-For (его ставит Caddy), иначе адрес соединения.
 */
public class RateLimitFilter implements WebFilter, Ordered {

    private final RateLimiter limiter;
    private final List<Compiled> rules;
    private final boolean enabled;

    /**
     * Фильтр.
     *
     * @param props   правила
     * @param limiter счётчики
     */
    public RateLimitFilter(RateLimitProperties props, RateLimiter limiter) {
        this.limiter = limiter;
        this.enabled = props.enabled();
        PathPatternParser parser = new PathPatternParser();
        this.rules = props.rules().stream().map(r -> new Compiled(r, parser.parse(r.path()))).toList();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        String method = exchange.getRequest().getMethod().name();
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        for (Compiled c : rules) {
            if (c.rule().method().equalsIgnoreCase(method) && c.pattern().matches(path)) {
                long wait = limiter.acquire(c.rule().id() + "|" + clientIp(exchange), c.rule().limit(), c.rule().window());
                if (wait > 0) {
                    return tooMany(exchange.getResponse(), wait);
                }
            }
        }
        return chain.filter(exchange);
    }

    static String clientIp(ServerWebExchange exchange) {
        String fwd = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            // Последний адрес добавил наш Caddy (он не доверяет входящему XFF), подделать его клиент не может.
            String[] hops = fwd.split(",");
            return hops[hops.length - 1].trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private static Mono<Void> tooMany(ServerHttpResponse rs, long wait) {
        rs.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        rs.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(wait));
        rs.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"code\":\"RATE_LIMITED\",\"detail\":\"Слишком много запросов\","
                + "\"hint\":\"Подождите " + wait + " с и повторите\"}";
        DataBuffer buf = rs.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return rs.writeWith(Mono.just(buf));
    }

    private record Compiled(RateLimitProperties.Rule rule, PathPattern pattern) {
    }
}
