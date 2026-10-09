package ru.proxodnaya.people.features.device.model;

import java.time.Instant;

/**
 * Выданный инвайт. Код показывается один раз, в БД — только HMAC.
 *
 * @param code      код (6 символов)
 * @param link      ссылка для телефона
 * @param expiresAt действует до
 */
public record IssuedInvite(String code, String link, Instant expiresAt) {
}
