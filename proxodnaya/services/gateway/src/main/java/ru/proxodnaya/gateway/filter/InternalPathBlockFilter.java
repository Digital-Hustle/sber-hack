package ru.proxodnaya.gateway.filter;

import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import ru.proxodnaya.gateway.exception.Problems;

/**
 * {@code /internal/**} и {@code /actuator/**} сервисов наружу не выходят: 404 до проверки токена (GW-04).
 * Сравнение по декодированному пути без регистра — {@code /%69nternal/} и {@code /INTERNAL/} тоже закрыты.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalPathBlockFilter implements WebFilter {

    /** Actuator самого gateway живёт на management-порту (корень пути), actuator сервисов — нет. */
    private static final String ACTUATOR = "/actuator";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath().toLowerCase(Locale.ROOT);
        if (blocked(path)) {
            return Problems.write(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "Ресурс не найден");
        }
        return chain.filter(exchange);
    }

    static boolean blocked(String path) {
        String normalized = path.replace('\\', '/').replaceAll("/+", "/");
        return normalized.contains("/internal/") || normalized.endsWith("/internal")
                || normalized.contains(ACTUATOR) && !normalized.startsWith(ACTUATOR)
                || normalized.contains("/../") || normalized.contains("/./") || normalized.contains(";");
    }
}
