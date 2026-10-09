package ru.proxodnaya.people.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.worker.gateway.FaceGateway;
import ru.proxodnaya.people.features.worker.model.FaceEnrollment;
import ru.proxodnaya.people.features.worker.model.FaceRejectedException;

/**
 * Контракт с face: тела запросов и перевод ошибок.
 */
class FaceGatewayTest {

    private static final UUID WORKER = UUID.fromString("0190a000-0000-7000-8000-000000000001");
    private static final String BASE = "http://face";

    private MockRestServiceServer server;
    private FaceGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new FaceGateway(builder.build(), new ObjectMapper());
    }

    @Test
    void enroll_sendsReplaceAndParsesResult() {
        // given
        server.expect(requestTo(BASE + "/internal/face/enroll")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"workerId\":\"" + WORKER + "\",\"frames\":[\"a\"],\"replace\":true}"))
                .andRespond(withSuccess("{\"accepted\":1,\"quality\":0.8,\"templates\":1}", MediaType.APPLICATION_JSON));

        // when
        FaceEnrollment result = gateway.enroll(WORKER, List.of("a"));

        // then
        assertThat(result).isEqualTo(new FaceEnrollment(1, 0.8, 1));
        server.verify();
    }

    @Test
    void enroll_422_rejectedWithReason() {
        // given
        server.expect(requestTo(BASE + "/internal/face/enroll")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"code\":\"FACE_LOW_QUALITY\",\"detail\":\"Слишком темно\","
                        + "\"errors\":[{\"field\":\"frames\",\"code\":\"TOO_DARK\"}]}"));

        // when / then
        assertThatThrownBy(() -> gateway.enroll(WORKER, List.of("a")))
                .isInstanceOfSatisfying(FaceRejectedException.class, error -> {
                    assertThat(error.code()).isEqualTo(ErrorCode.FACE_LOW_QUALITY);
                    assertThat(error.reason()).isEqualTo("TOO_DARK");
                    assertThat(error.getMessage()).isEqualTo("Слишком темно");
                });
    }

    @Test
    void enroll_differentPeople_faceMultiple() {
        // given
        server.expect(requestTo(BASE + "/internal/face/enroll")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("{\"code\":\"FACE_MULTIPLE\",\"detail\":\"На снимках разные люди\"}"));

        // when / then
        assertThatThrownBy(() -> gateway.enroll(WORKER, List.of("a", "b")))
                .isInstanceOfSatisfying(FaceRejectedException.class, error -> {
                    assertThat(error.code()).isEqualTo(ErrorCode.FACE_MULTIPLE);
                    assertThat(error.reason()).isNull();
                });
    }

    @Test
    void serverErrorOrForbidden_serviceUnavailable() {
        // given
        server.expect(requestTo(BASE + "/internal/face/workers/" + WORKER)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());
        server.expect(requestTo(BASE + "/internal/face/check")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        // when / then
        assertThatThrownBy(() -> gateway.delete(WORKER)).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> gateway.check("a")).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void delete_204_ok() {
        // given
        server.expect(requestTo(BASE + "/internal/face/workers/" + WORKER)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        // when
        gateway.delete(WORKER);

        // then
        server.verify();
    }
}
