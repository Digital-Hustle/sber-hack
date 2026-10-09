package ru.documents.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Внутренние ручки сервисов (`/internal/**`) снаружи не существуют: 404 до проверки токена (FR-64),
 * чтобы по ответу нельзя было понять, что такой путь есть.
 */
public class InternalPathFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isInternal(exchange.getRequest().getPath().pathWithinApplication())) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    static boolean isInternal(PathContainer path) {
        for (PathContainer.Element e : path.elements()) {
            if (e instanceof PathContainer.PathSegment seg && "internal".equalsIgnoreCase(seg.valueToMatch().trim())) {
                return true;
            }
        }
        return false;
    }
}
