package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Опоздания и переработки за период.
 *
 * @param from    начало
 * @param to      конец, не включая
 * @param items   по рабочим, больше опозданий — выше
 * @param details по сменам (только с фильтром {@code worker})
 * @param method  как посчитано
 */
public record LateOvertimeRs(Instant from, Instant to, List<WorkerLateOvertimeRs> items, List<ShiftOutcomeRs> details,
                             String method) {

    /**
     * Итоги рабочего.
     *
     * @param workerId        рабочий
     * @param shifts          смен
     * @param late            с опозданием
     * @param lateMinutes     минут опозданий
     * @param absent          неявок
     * @param overtime        смен с переработкой
     * @param overtimeMinutes минут переработки
     * @param incomplete      смен без отмеченного выхода
     */
    public record WorkerLateOvertimeRs(String workerId, long shifts, long late, long lateMinutes, long absent, long overtime,
                                       long overtimeMinutes, long incomplete) {
    }

    /**
     * Смена.
     *
     * @param shiftId         смена
     * @param start           начало по графику
     * @param end             конец по графику
     * @param arrival         ON_TIME | LATE | ABSENT | NOT_YET
     * @param firstEntry      первый вход
     * @param lastExit        последний выход
     * @param lateMinutes     минут опоздания
     * @param overtimeMinutes минут после конца смены
     * @param overtime        переработка не меньше порога
     * @param incomplete      выход не отмечен
     */
    public record ShiftOutcomeRs(UUID shiftId, Instant start, Instant end, String arrival, Instant firstEntry, Instant lastExit,
                                 long lateMinutes, long overtimeMinutes, boolean overtime, boolean incomplete) {
    }
}
