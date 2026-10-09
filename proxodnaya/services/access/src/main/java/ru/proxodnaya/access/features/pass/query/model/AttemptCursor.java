package ru.proxodnaya.access.features.pass.query.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Непрозрачный keyset-курсор журнала: base64url от {@code ts~id} последней строки страницы. Сортировка
 * {@code (ts desc, id desc)}: новые записи сверху не сдвигают уже загруженные страницы, у одинаковых {@code ts} нет дублей и пропусков.
 *
 * @param ts время последней строки
 * @param id id последней строки
 */
public record AttemptCursor(Instant ts, UUID id) {

    private static final String SEP = "~";

    /**
     * Кодирует курсор.
     *
     * @return строка для клиента
     */
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((ts + SEP + id).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Декодирует курсор.
     *
     * @param cursor курсор от клиента или {@code null}
     * @return курсор или {@code null} для первой страницы
     * @throws DomainException VALIDATION_ERROR, если курсор испорчен
     */
    public static AttemptCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            int sep = raw.indexOf(SEP);
            if (sep < 0) {
                throw new IllegalArgumentException("no separator");
            }
            return new AttemptCursor(Instant.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
        } catch (IllegalArgumentException | DateTimeParseException error) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Неверный курсор страницы");
        }
    }
}
