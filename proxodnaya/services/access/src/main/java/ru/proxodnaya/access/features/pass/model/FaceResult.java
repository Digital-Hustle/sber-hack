package ru.proxodnaya.access.features.pass.model;

/**
 * Результат сверки лица от Python-сервиса face.
 *
 * @param match     совпадает ли лицо с эталоном
 * @param score     косинусная близость (0..1)
 * @param liveness  прошла ли проверка живости
 * @param quality   качество кадра (0..1)
 * @param reason    причина отказа (если не match)
 */
public record FaceResult(
        boolean match,
        double score,
        boolean liveness,
        double quality,
        String reason
) {
    public static FaceResult ok(double score, double quality) {
        return new FaceResult(true, score, true, quality, null);
    }

    public static FaceResult deny(String reason, double score, double quality) {
        return new FaceResult(false, score, false, quality, reason);
    }
}
