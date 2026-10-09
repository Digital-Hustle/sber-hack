package ru.proxodnaya.people.api.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Публичные ключи токенов people (RFC 7517).
 *
 * @param keys JWK (kty, crv, x, y, kid, use) — без закрытой части
 */
public record JwksRs(List<Map<String, Object>> keys) {
}
