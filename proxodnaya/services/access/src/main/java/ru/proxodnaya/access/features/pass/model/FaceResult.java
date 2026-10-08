package ru.proxodnaya.access.features.pass.model;

/**
 * Ответ face на сверку 1:1 с владельцем QR.
 *
 * @param match    лицо совпало с эталоном
 * @param score    близость 0..1
 * @param liveness живость подтверждена
 * @param quality  качество кадров 0..1
 * @param reason   причина от face (FACE_NOT_FOUND, FACE_MULTIPLE, FACE_NOT_ENROLLED, ...) или пусто
 */
public record FaceResult(boolean match, double score, boolean liveness, double quality, String reason) {
}
