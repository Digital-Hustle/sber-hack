package ru.proxodnaya.gateway.units;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import ru.proxodnaya.gateway.filter.InternalPathBlockFilter;

/**
 * Какие пути не уходят наружу.
 */
class InternalPathBlockFilterTest {

    private final InternalPathBlockFilter filter = new InternalPathBlockFilter();

    @Test
    void blocksInternalActuatorAndTraversal() {
        for (String path : new String[] {"/people/internal/people/x", "/access/internal", "/x/actuator/env", "/a/../internal/x",
            "/a/;jsessionid=1/x", "/a//internal//x"}) {
            assertThat(status(path)).as(path).isEqualTo(404);
        }
    }

    @Test
    void passesNormalPaths() {
        for (String path : new String[] {"/people/api/v1/workers", "/shift/api/v1/stream/situation", "/actuator/health/liveness",
            "/people/api/v1/workers/internally"}) {
            assertThat(status(path)).as(path).isEqualTo(200);
        }
    }

    private int status(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
        WebFilterChain chain = ignored -> {
            exchange.getResponse().setRawStatusCode(200);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return exchange.getResponse().getStatusCode().value();
    }
}
