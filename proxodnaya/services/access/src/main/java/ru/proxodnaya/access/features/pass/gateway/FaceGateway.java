package ru.proxodnaya.access.features.pass.gateway;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.shared.ChallengeAction;

/**
 * Сервис face: {@code POST /internal/face/verify} — сверка 1:1 с эталоном владельца QR и живость.
 * Любая ошибка пробрасывается: решает вызывающий (fail-closed).
 */
@Component
public class FaceGateway {

    private final RestClient client;

    public FaceGateway(@Qualifier("face") RestClient client) {
        this.client = client;
    }

    /**
     * Сверяет кадры с эталоном.
     *
     * @param workerId  владелец QR
     * @param frames    кадры JPEG base64
     * @param challenge действие челленджа
     * @return результат face
     * @throws IllegalStateException если face ответил пустым телом
     */
    public FaceResult verify(String workerId, List<String> frames, ChallengeAction challenge) {
        FaceResult result = client.post()
                .uri("/internal/face/verify")
                .body(new VerifyRq(workerId, frames, challenge.name()))
                .retrieve()
                .body(FaceResult.class);
        if (result == null) {
            throw new IllegalStateException("face вернул пустой ответ");
        }
        return result;
    }

    private record VerifyRq(String workerId, List<String> frames, String challenge) {
    }
}
