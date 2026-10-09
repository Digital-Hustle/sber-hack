package ru.proxodnaya.shift.features.alert.command.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Раз в минуту прогоняет правила оповещений и напоминания о выходе. Один инстанс — блокировка не нужна.
 */
@Component
public class AlertJob {

    private static final Logger LOG = LoggerFactory.getLogger(AlertJob.class);

    private final AlertService alerts;
    private final ExitReminderService reminders;

    public AlertJob(AlertService alerts, ExitReminderService reminders) {
        this.alerts = alerts;
        this.reminders = reminders;
    }

    /**
     * Шаг правил и напоминаний.
     */
    @Scheduled(fixedDelayString = "${shift.alerts.every:PT1M}", initialDelayString = "${shift.alerts.every:PT1M}")
    public void run() {
        int raised = alerts.runRules();
        if (raised > 0) {
            LOG.info("shift: поднято оповещений: {}", raised);
        }
        int reminded = reminders.remindOverdue();
        if (reminded > 0) {
            LOG.info("shift: напоминаний о выходе: {}", reminded);
        }
    }
}
