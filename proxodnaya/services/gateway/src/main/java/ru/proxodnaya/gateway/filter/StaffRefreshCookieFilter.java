package ru.proxodnaya.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Cookies не нужны сервисам, кроме HttpOnly refresh персонала. Пропускаем только его и только в POST refresh.
 */
@Component
public class StaffRefreshCookieFilter implements GlobalFilter, Ordered {

    private static final String REFRESH_PATH = "/people/api/v1/auth/staff/refresh";
    private static final String COOKIE_NAME = "gate_staff_rt";
    private static final String TOKEN_FORMAT = "[A-Za-z0-9_-]{43}";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest source = exchange.getRequest();
        HttpCookie refresh = HttpMethod.POST.equals(source.getMethod()) && REFRESH_PATH.equals(source.getURI().getPath())
                ? source.getCookies().getFirst(COOKIE_NAME) : null;
        ServerHttpRequest request = source.mutate().headers(headers -> {
            headers.remove(HttpHeaders.COOKIE);
            if (refresh != null && refresh.getValue().matches(TOKEN_FORMAT)) {
                headers.set(HttpHeaders.COOKIE, refresh.toString());
            }
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
