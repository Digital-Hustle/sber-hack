package ru.proxodnaya.people.features.staff.model;

/**
 * Код отправлен (ответ одинаковый для любого адреса).
 *
 * @param sentTo    замаскированный адрес
 * @param expiresAt код действует до, Unix ms
 * @param resendAt  повторно можно с, Unix ms
 */
public record CodeSent(String sentTo, long expiresAt, long resendAt) {
}
