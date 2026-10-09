package ru.proxodnaya.people.api.dto.response;

/**
 * Эталон лица заведён.
 *
 * @param accepted  принято снимков
 * @param quality   качество 0..1 (худший снимок)
 * @param templates эталонов у рабочего
 */
public record FaceEnrollRs(int accepted, double quality, int templates) {
}
