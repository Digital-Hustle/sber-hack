package ru.proxodnaya.people.api.dto.response;

import java.time.Instant;

/**
 * Инвайт: код показывается один раз.
 *
 * @param inviteCode код
 * @param link       ссылка для телефона (её же кладём в QR)
 * @param qrPayload  содержимое QR (= link)
 * @param expiresAt  действует до
 */
public record InviteRs(String inviteCode, String link, String qrPayload, Instant expiresAt) {
}
