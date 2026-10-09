package ru.proxodnaya.shift.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import ru.proxodnaya.shift.api.dto.response.AlertListRs;
import ru.proxodnaya.shift.api.dto.response.AlertRs;
import ru.proxodnaya.shift.api.dto.response.HeartbeatRs;
import ru.proxodnaya.shift.features.alert.model.Alert;
import ru.proxodnaya.shift.features.alert.model.HeartbeatResult;

/**
 * Оповещения и heartbeat → DTO.
 */
@Component
public class AlertApiMapper {

    /**
     * Оповещение.
     *
     * @param alert модель
     * @return DTO
     */
    public AlertRs convert(Alert alert) {
        return new AlertRs(alert.id(), alert.type().name(), alert.severity(), alert.workerId(), alert.zoneId(), alert.details(),
                alert.createdAt(), alert.resolvedAt(), alert.ackedBy(), alert.ackedAt());
    }

    /**
     * Лента.
     *
     * @param alerts модели
     * @return DTO
     */
    public AlertListRs convert(List<Alert> alerts) {
        return new AlertListRs(alerts.stream().map(this::convert).toList());
    }

    /**
     * Heartbeat.
     *
     * @param result результат
     * @return DTO
     */
    public HeartbeatRs convert(HeartbeatResult result) {
        return new HeartbeatRs(result.at(), result.insideFence(), result.distanceM());
    }
}
