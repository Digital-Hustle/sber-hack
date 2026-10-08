package ru.proxodnaya.people.shared;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Непрозрачный курсор keyset-пагинации: base64url от id последней строки (UUIDv7 упорядочен по времени).
 */
public final class Cursor {

    private Cursor() {
    }

    /**
     * Кодирует курсор.
     *
     * @param lastId id последней строки страницы
     * @return курсор
     */
    public static String encode(UUID lastId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(lastId.toString().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Декодирует курсор.
     *
     * @param cursor курсор от клиента или {@code null}
     * @return id или {@code null} для первой страницы
     * @throws DomainException VALIDATION_ERROR, если курсор испорчен
     */
    public static UUID decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException error) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Неверный курсор страницы");
        }
    }
}
