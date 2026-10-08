package ru.proxodnaya.access.features.pass.gateway;

import java.util.List;

/**
 * Клиент к Python-сервису face: сверка 1:1 + живость.
 * Таймаут 1.5 с, circuit breaker, fail-closed (FR-09, NFR-16).
 */
public interface FaceClient {

    /**
     * @param workerId  id рабочего, чей эталон берём
     * @param frames    JPEG base64 (3–5 кадров)
     * @param challengeAction действие челленджа
     * @return результат сверки
     */
    FaceVerifyResult verify(String workerId, List<String> frames, String challengeAction);
}
