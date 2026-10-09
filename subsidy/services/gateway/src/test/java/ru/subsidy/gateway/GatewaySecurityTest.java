package ru.subsidy.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    private WebTestClient web;

    @Test
    void specialistApiWithoutTokenIs401() {
        for (String svc : new String[] {"package", "docproc", "rules", "assistant"}) {
            web.get().uri("/" + svc + "/api/v1/ping").exchange().expectStatus().isUnauthorized();
        }
    }

    @Test
    void internalIsClosed() {
        web.get().uri("/package/internal/package/files/1").exchange().expectStatus().is4xxClientError();
        web.get().uri("/unknown/api/v1/x").exchange().expectStatus().is4xxClientError();
    }
}
