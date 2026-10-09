package ru.proxodnaya.shift.features.shift.api;

import java.time.Instant;
import java.util.Optional;

/**
 * Узкий интерфейс смен для других фич (интервалы, кабинет рабочего).
 */
public interface ShiftLookup {

    /**
     * Смена рабочего в момент {@code moment}: окно смены (с ранним входом) или, если вне окна, смена того же дня.
     *
     * @param workerId рабочий
     * @param moment   момент
     * @return смена с признаком {@code windowOk} или пусто, если смен в этот день нет
     */
    Optional<ActiveShift> activeShift(String workerId, Instant moment);
}
