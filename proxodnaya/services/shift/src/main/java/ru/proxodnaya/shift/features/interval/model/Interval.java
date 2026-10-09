package ru.proxodnaya.shift.features.interval.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Интервал присутствия рабочего в зоне.
 *
 * @param id          id
 * @param workerId    рабочий
 * @param zoneId      зона или {@code null}
 * @param shiftId     смена, в окно которой пришёлся вход, или {@code null}
 * @param enteredAt   вход
 * @param exitedAt    выход или {@code null}, пока открыт
 * @param closeReason причина закрытия или {@code null}
 * @param flags       пометки
 * @param simulated   из симулятора
 */
public record Interval(UUID id, String workerId, String zoneId, UUID shiftId, Instant enteredAt, Instant exitedAt,
                       CloseReason closeReason, List<IntervalFlag> flags, boolean simulated) {

    /**
     * Конец интервала не подтверждён выходом.
     *
     * @return {@code true}, если есть {@link IntervalFlag#UNCONFIRMED_EXIT}
     */
    public boolean unconfirmed() {
        return flags.contains(IntervalFlag.UNCONFIRMED_EXIT);
    }
}
