package ru.proxodnaya.shift.features.analytics.query.model;

/**
 * Итоги рабочего за период.
 *
 * @param workerId        рабочий
 * @param shifts          смен, начавшихся в периоде
 * @param late            из них с опозданием
 * @param lateMinutes     минут опозданий всего
 * @param absent          смен без входа
 * @param overtime        смен с переработкой
 * @param overtimeMinutes минут переработки всего
 * @param incomplete      смен без отмеченного выхода
 */
public record WorkerLateOvertime(String workerId, long shifts, long late, long lateMinutes, long absent, long overtime,
                                 long overtimeMinutes, long incomplete) {
}
