package ru.documents.gateway;

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
    void operatorApiWithoutTokenIs401() {
        web.get().uri("/review/api/v1/applications").exchange().expectStatus().isUnauthorized();
        web.get().uri("/docproc/api/v1/ping").exchange().expectStatus().isUnauthorized();
        web.get().uri("/intake/api/v1/operator/applications/x/documents").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void internalIsClosed() {
        web.get().uri("/intake/internal/intake/files/1").exchange().expectStatus().is4xxClientError();
    }
}
