package ru.proxodnaya.people.features.worker.model;

/**
 * Итог заведения эталона лица в face.
 *
 * @param accepted  сколько снимков принято
 * @param quality   худшее качество среди снимков, 0..1
 * @param templates сколько эталонов теперь у рабочего
 */
public record FaceEnrollment(int accepted, double quality, int templates) {
}
