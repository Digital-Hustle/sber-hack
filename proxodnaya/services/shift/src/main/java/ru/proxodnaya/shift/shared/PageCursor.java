package ru.proxodnaya.shift.shared;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Keyset-курсор страницы: момент + id последней строки (строки с одинаковым временем не теряются и не дублируются).
 *
 * @param ts момент последней показанной строки
 * @param id id последней показанной строки
 */
public record PageCursor(Instant ts, UUID id) {

    private static final String SEP = "~";

    /**
     * Кодирует курсор в непрозрачную строку для {@code page.next}.
     *
     * @return base64url
     */
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((ts + SEP + id).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Разбирает курсор из запроса.
     *
     * @param cursor строка из {@code page.next} или {@code null}
     * @return курсор или {@code null} для первой страницы
     * @throws DomainException VALIDATION_ERROR, если строка не курсор
     */
    public static PageCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            int sep = raw.indexOf(SEP);
            if (sep < 0) {
                throw new IllegalArgumentException("no separator");
            }
            return new PageCursor(Instant.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
        } catch (IllegalArgumentException | DateTimeParseException error) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Неверный курсор страницы");
        }
    }
}
