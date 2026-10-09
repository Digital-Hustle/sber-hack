package ru.proxodnaya.people.features.worker.gateway;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.worker.model.FaceCheck;
import ru.proxodnaya.people.features.worker.model.FaceEnrollment;
import ru.proxodnaya.people.features.worker.model.FaceRejectedException;

/**
 * Сервис face: {@code /internal/face/enroll|check|workers/{id}}. Отказ по качеству → 422, недоступность → 503.
 */
@Component
public class FaceGateway {

    private static final String UNAVAILABLE = "Сервис распознавания лиц недоступен, попробуйте позже";
    private static final String CODE = "code";

    private final RestClient client;
    private final ObjectMapper json;

    public FaceGateway(@Qualifier("face") RestClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    /**
     * Заводит эталоны рабочего (прежние заменяются).
     *
     * @param workerId рабочий
     * @param frames   1..3 снимка JPEG base64
     * @return итог
     * @throws FaceRejectedException снимок не годится
     * @throws DomainException       SERVICE_UNAVAILABLE
     */
    public FaceEnrollment enroll(UUID workerId, List<String> frames) {
        return call(() -> client.post().uri("/internal/face/enroll")
                .body(new EnrollRq(workerId.toString(), frames, true))
                .retrieve().body(FaceEnrollment.class));
    }

    /**
     * Проверяет снимок до сохранения.
     *
     * @param photo JPEG base64
     * @return вердикт
     */
    public FaceCheck check(String photo) {
        return call(() -> client.post().uri("/internal/face/check").body(new CheckRq(photo)).retrieve().body(FaceCheck.class));
    }

    /**
     * Удаляет все эталоны рабочего (идемпотентно).
     *
     * @param workerId рабочий
     */
    public void delete(UUID workerId) {
        call(() -> client.delete().uri("/internal/face/workers/{id}", workerId).retrieve().toBodilessEntity());
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        T result;
        try {
            result = request.get();
        } catch (RestClientResponseException error) {
            throw translate(error);
        } catch (RestClientException error) {
            throw new DomainException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        if (result == null) {
            throw new DomainException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        return result;
    }

    private DomainException translate(RestClientResponseException error) {
        if (error.getStatusCode().value() != HttpStatus.UNPROCESSABLE_ENTITY.value()) {
            return new DomainException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        JsonNode body = parse(error.getResponseBodyAsString());
        ErrorCode code = "FACE_MULTIPLE".equals(body.path(CODE).asText()) ? ErrorCode.FACE_MULTIPLE : ErrorCode.FACE_LOW_QUALITY;
        String detail = body.path("detail").asText("Снимок не подходит для эталона");
        JsonNode reason = body.path("errors").path(0).path(CODE);
        return new FaceRejectedException(code, detail, reason.isMissingNode() ? null : reason.asText());
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            return json.createObjectNode();
        }
    }

    private record EnrollRq(String workerId, List<String> frames, boolean replace) {
    }

    private record CheckRq(String photo) {
    }
}
