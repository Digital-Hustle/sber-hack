package ru.proxodnaya.access.api.dto.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Страница журнала проходов.
 *
 * @param attempts список
 * @param nextCursor курсор для следующей страницы или null
 * @param total всего записей по фильтру
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptsRs(
        List<AttemptRs> attempts,
        String nextCursor,
        long total
) {
}
