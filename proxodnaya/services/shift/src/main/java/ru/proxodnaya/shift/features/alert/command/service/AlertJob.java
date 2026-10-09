package ru.proxodnaya.shift.features.alert.command.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Раз в минуту прогоняет правила оповещений. Один инстанс — блокировка не нужна.
 */
@Component
public class AlertJob {

    private static final Logger LOG = LoggerFactory.getLogger(AlertJob.class);

    private final AlertService alerts;

    public AlertJob(AlertService alerts) {
        this.alerts = alerts;
    }

    /**
     * Шаг правил.
     */
    @Scheduled(fixedDelayString = "${shift.alerts.every:PT1M}", initialDelayString = "${shift.alerts.every:PT1M}")
    public void run() {
        int raised = alerts.runRules();
        if (raised > 0) {
            LOG.info("shift: поднято оповещений: {}", raised);
        }
    }
}
