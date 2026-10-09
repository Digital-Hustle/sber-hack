package ru.proxodnaya.gateway.filter;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import reactor.core.publisher.Mono;
import ru.proxodnaya.gateway.config.properties.GatewayProperties;
import ru.proxodnaya.gateway.exception.Problems;

/**
 * Лимит на перебор инвайтов и попытки киоска (GW-05): token bucket на адрес клиента, 429 + Retry-After.
 * Адрес — последний хоп {@code X-Forwarded-For} (его дописывает Caddy) или адрес соединения.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter implements WebFilter {

    private static final int MAX_CLIENTS = 100_000;
    private static final long MILLIS_IN_SECOND = 1000L;

    private final List<PathPattern> patterns;
    private final int capacity;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(GatewayProperties props, Clock clock) {
        this.patterns = props.rateLimit().paths().stream().map(PathPatternParser.defaultInstance::parse).toList();
        this.capacity = props.rateLimit().capacity();
        this.window = props.rateLimit().window();
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        if (patterns.stream().noneMatch(pattern -> pattern.matches(path))) {
            return chain.filter(exchange);
        }
        if (buckets.size() > MAX_CLIENTS) {
            buckets.clear();
        }
        long now = clock.millis();
        Bucket bucket = buckets.compute(client(exchange), (key, current) -> refill(current, now));
        if (bucket.used() > capacity) {
            long retry = Math.max(1, (bucket.windowStart() + window.toMillis() - now + MILLIS_IN_SECOND - 1) / MILLIS_IN_SECOND);
            exchange.getResponse().getHeaders().set("Retry-After", Long.toString(retry));
            return Problems.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Слишком много попыток, подождите");
        }
        return chain.filter(exchange);
    }

    private Bucket refill(Bucket current, long now) {
        if (current == null || now - current.windowStart() >= window.toMillis()) {
            return new Bucket(now, 1);
        }
        // сверх лимита счётчик не растёт: отказы не продлевают блокировку
        return new Bucket(current.windowStart(), Math.min(current.used() + 1, capacity + 1));
    }

    private static String client(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].strip();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private record Bucket(long windowStart, int used) {
    }
}
