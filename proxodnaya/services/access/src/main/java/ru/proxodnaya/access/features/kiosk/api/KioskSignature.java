package ru.proxodnaya.access.features.kiosk.api;

/**
 * Заголовки подписи запроса терминала.
 *
 * @param kioskId   {@code X-Kiosk-Id}
 * @param ts        {@code X-Ts}, Unix ms
 * @param nonce     {@code X-Nonce}, одноразовый
 * @param signature {@code X-Signature}, ECDSA P-256 base64url над
 *                  {@code kiosk|<kioskId>|<METHOD>|<path?query>|<ts>|<nonce>|<sha256b64u(body)>}
 */
public record KioskSignature(String kioskId, String ts, String nonce, String signature) {
}
