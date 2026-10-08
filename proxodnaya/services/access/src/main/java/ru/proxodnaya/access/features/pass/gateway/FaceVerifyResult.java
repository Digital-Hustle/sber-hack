package ru.proxodnaya.access.features.pass.gateway;

/**
 * Результат проверки лица от Python-сервиса face (/internal/face/verify).
 *
 * @param match     совпадает ли с эталоном
 * @param score     косинусная близость
 * @param liveness  прошла ли живость
 * @param quality   качество кадра
 * @param reason    причина (если не match)
 */
public record FaceVerifyResult(
        boolean match,
        double score,
        boolean liveness,
        double quality,
        String reason
) {}
