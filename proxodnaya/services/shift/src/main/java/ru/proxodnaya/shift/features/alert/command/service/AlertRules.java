package ru.proxodnaya.shift.features.alert.command.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.proxodnaya.shift.features.alert.model.AlertCandidate;
import ru.proxodnaya.shift.features.alert.model.AlertType;
import ru.proxodnaya.shift.features.alert.model.OnSiteWorker;

/**
 * Правила B-01 без состояния: кто из людей в смене вне геозоны или молчит.
 *
 * @param fenceEnabled  геозона задана
 * @param noSignalAfter порог «нет связи»
 */
public record AlertRules(boolean fenceEnabled, Duration noSignalAfter) {

    /**
     * Проверяет людей на объекте.
     *
     * @param workers люди в смене на объекте
     * @param now     текущий момент
     * @return найденные условия
     */
    public List<AlertCandidate> evaluate(List<OnSiteWorker> workers, Instant now) {
        List<AlertCandidate> found = new ArrayList<>();
        Instant silentBefore = now.minus(noSignalAfter);
        for (OnSiteWorker worker : workers) {
            if (worker.heartbeatAt() == null) {
                continue;
            }
            boolean fresh = worker.heartbeatAt().isAfter(worker.enteredAt());
            if (fenceEnabled && fresh && Boolean.FALSE.equals(worker.insideFence())) {
                found.add(new AlertCandidate(AlertType.LEFT_SITE, worker.workerId(), worker.zoneId(),
                        String.format(Locale.ROOT, "Вне геозоны: %.0f м от центра площадки", worker.distanceM())));
            } else if (worker.heartbeatAt().isBefore(silentBefore) && worker.enteredAt().isBefore(silentBefore)) {
                found.add(new AlertCandidate(AlertType.NO_SIGNAL, worker.workerId(), worker.zoneId(),
                        "Нет связи с телефоном " + Duration.between(worker.heartbeatAt(), now).toMinutes() + " мин"));
            }
        }
        return found;
    }
}
