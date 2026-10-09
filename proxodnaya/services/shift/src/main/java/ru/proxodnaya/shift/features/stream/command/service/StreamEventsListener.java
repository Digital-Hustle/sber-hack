package ru.proxodnaya.shift.features.stream.command.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ru.proxodnaya.shift.features.alert.api.AlertRaised;
import ru.proxodnaya.shift.features.interval.api.PassRecorded;
import ru.proxodnaya.shift.features.interval.api.PresenceChanged;
import ru.proxodnaya.shift.features.stream.model.StreamKind;

/**
 * Доменные события shift → SSE после коммита: откатившаяся транзакция в поток не попадает.
 */
@Component
public class StreamEventsListener {

    static final String ATTEMPT_CREATED = "attempt.created";
    static final String ATTEMPT_RESULT = "attempt.result";
    static final String PRESENCE_CHANGED = "presence.changed";
    static final String ALERT_CREATED = "alert.created";
    private static final String WORKER_ID = "workerId";
    private static final String ZONE_ID = "zoneId";
    private static final String SIMULATED = "simulated";
    private static final String SOURCE = "source";

    private final SseHub hub;

    public StreamEventsListener(SseHub hub) {
        this.hub = hub;
    }

    /**
     * Попытка прохода: в обстановку и, если человек опознан, ему на телефон.
     *
     * @param event попытка
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(PassRecorded event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("attemptId", event.attemptId().toString());
        data.put(WORKER_ID, event.workerId());
        data.put("checkpointId", event.checkpointId());
        data.put(ZONE_ID, event.zoneId());
        data.put("direction", event.direction());
        data.put("decision", event.decision());
        data.put("reasonCode", event.reasonCode());
        data.put(SOURCE, event.source());
        data.put("ts", event.ts().toString());
        data.put(SIMULATED, event.simulated());
        hub.publish(ATTEMPT_CREATED, StreamKind.SITUATION, null, data);
        if (event.workerId() != null) {
            Map<String, Object> mine = new LinkedHashMap<>(data);
            mine.remove(WORKER_ID);
            mine.remove(SOURCE);
            hub.publish(ATTEMPT_RESULT, StreamKind.WORKER, event.workerId(), mine);
        }
    }

    /**
     * Вход/выход из зоны: «сейчас на объекте».
     *
     * @param event изменение присутствия
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(PresenceChanged event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(WORKER_ID, event.workerId());
        data.put(ZONE_ID, event.zoneId());
        data.put("inside", event.inside());
        data.put("at", event.at().toString());
        data.put("closeReason", event.closeReason());
        data.put(SIMULATED, event.simulated());
        hub.publish(PRESENCE_CHANGED, StreamKind.SITUATION, null, data);
    }

    /**
     * Поднято оповещение: в обстановку.
     *
     * @param event оповещение
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(AlertRaised event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alertId", event.alertId().toString());
        data.put("type", event.type());
        data.put("severity", event.severity());
        data.put(WORKER_ID, event.workerId());
        data.put(ZONE_ID, event.zoneId());
        data.put("details", event.details());
        data.put("createdAt", event.createdAt().toString());
        hub.publish(ALERT_CREATED, StreamKind.SITUATION, null, data);
    }
}
