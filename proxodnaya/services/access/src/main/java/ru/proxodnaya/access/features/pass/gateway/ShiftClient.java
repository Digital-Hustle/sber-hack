package ru.proxodnaya.access.features.pass.gateway;

import java.time.Instant;

/**
 * Клиент к shift: активная смена рабочего.
 * Таймаут 1.5 с, circuit breaker, fail-closed.
 */
public interface ShiftClient {

    /**
     * @param workerId id рабочего
     * @param at        момент времени
     * @return активная смена или null если нет смены
     */
    ActiveShift getActiveShift(String workerId, Instant at);
}
