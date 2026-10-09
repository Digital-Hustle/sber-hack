package ru.proxodnaya.shift.features.interval.command.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Раз в 5 минут закрывает интервалы, у которых нет выхода через {@code AUTO_CLOSE_AFTER} после конца смены (FR-23).
 * Один инстанс сервиса — распределённая блокировка не нужна; строки берутся {@code skip locked}.
 */
@Component
public class AutoCloseJob {

    private static final Logger LOG = LoggerFactory.getLogger(AutoCloseJob.class);

    private final IntervalService intervals;

    public AutoCloseJob(IntervalService intervals) {
        this.intervals = intervals;
    }

    /**
     * Закрывает пачками, пока есть что закрывать.
     */
    @Scheduled(fixedDelayString = "${shift.auto-close-every:PT5M}", initialDelayString = "${shift.auto-close-every:PT5M}")
    public void run() {
        int closed;
        int total = 0;
        do {
            closed = intervals.autoClose();
            total += closed;
        } while (closed == IntervalService.AUTO_CLOSE_BATCH);
        if (total > 0) {
            LOG.info("shift: автозакрыто интервалов без выхода: {}", total);
        }
    }
}
