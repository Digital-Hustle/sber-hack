package ru.proxodnaya.shift.features.alert.command.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.shift.config.properties.AlertProperties;
import ru.proxodnaya.shift.features.alert.command.repository.HeartbeatRepository;
import ru.proxodnaya.shift.features.alert.model.HeartbeatResult;

/**
 * Heartbeat телефона (B-01): храним только последний, геозону считаем сразу.
 */
@Service
@Transactional
public class HeartbeatService {

    private final HeartbeatRepository repository;
    private final AlertProperties props;
    private final Clock clock;

    public HeartbeatService(HeartbeatRepository repository, AlertProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Принимает heartbeat. Точность в пользу человека: в геозоне, если круг погрешности её задевает.
     *
     * @param workerId рабочий
     * @param lat      широта
     * @param lon      долгота
     * @param accuracy точность, м, или {@code null}
     * @return в геозоне ли и насколько далеко
     */
    public HeartbeatResult record(String workerId, double lat, double lon, Double accuracy) {
        Instant now = clock.instant();
        Double distance = null;
        Boolean inside = null;
        if (props.fenceEnabled()) {
            distance = Geofence.distance(props.siteLat(), props.siteLon(), lat, lon);
            inside = distance - (accuracy == null ? 0 : accuracy) <= props.siteRadius();
        }
        repository.save(workerId, lat, lon, accuracy, distance, inside, now);
        return new HeartbeatResult(now, inside, distance);
    }
}
