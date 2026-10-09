package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Интервал присутствия.
 *
 * @param id          id
 * @param workerId    рабочий
 * @param zoneId      зона
 * @param shiftId     смена или {@code null}
 * @param enteredAt   вход
 * @param exitedAt    выход или {@code null}
 * @param closeReason EXIT | ZONE_CHANGE | REENTRY | AUTO_SHIFT_END или {@code null}
 * @param flags       пометки (UNCONFIRMED_EXIT)
 * @param incomplete  конец не подтверждён или интервал висит дольше PRESENCE_TTL
 */
public record IntervalRs(UUID id, String workerId, @Schema(nullable = true) String zoneId, @Schema(nullable = true) UUID shiftId,
                         Instant enteredAt, @Schema(nullable = true) Instant exitedAt, @Schema(nullable = true) String closeReason,
                         List<String> flags, boolean incomplete) {
}
