package ru.proxodnaya.people.api.dto.response;

/**
 * Код отправлен (одинаково для любого адреса).
 *
 * @param sentTo    замаскированный адрес
 * @param expiresAt код действует до, Unix ms
 * @param resendAt  повторно можно с, Unix ms
 */
public record CodeSentRs(String sentTo, long expiresAt, long resendAt) {
}
