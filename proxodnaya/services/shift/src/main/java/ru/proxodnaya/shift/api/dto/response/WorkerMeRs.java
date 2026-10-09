package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Статус рабочего.
 *
 * @param inside        на объекте
 * @param zoneId        зона, если на объекте
 * @param since         на объекте с
 * @param shift         смена сейчас или сегодня
 * @param workedMsToday на объекте сегодня, мс
 */
public record WorkerMeRs(boolean inside, @Schema(nullable = true) String zoneId, @Schema(nullable = true) Instant since,
                         @Schema(nullable = true) ActiveShiftRs shift, long workedMsToday) {
}
