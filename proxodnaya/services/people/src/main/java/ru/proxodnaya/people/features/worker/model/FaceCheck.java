package ru.proxodnaya.people.features.worker.model;

/**
 * Годится ли снимок эталоном.
 *
 * @param ok      годится
 * @param code    OK | NO_FACE | MULTIPLE | TOO_SMALL | TOO_DARK | TOO_BRIGHT | BLURRY
 * @param quality качество 0..1
 * @param message подсказка человеку
 */
public record FaceCheck(boolean ok, String code, double quality, String message) {
}
