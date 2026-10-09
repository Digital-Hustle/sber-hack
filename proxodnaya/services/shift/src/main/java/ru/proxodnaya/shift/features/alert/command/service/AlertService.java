package ru.proxodnaya.shift.features.alert.command.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.event.Topics;
import ru.proxodnaya.common.exception.NotFoundException;
import ru.proxodnaya.shift.config.properties.AlertProperties;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.alert.api.AlertRaised;
import ru.proxodnaya.shift.features.alert.command.repository.AlertRepository;
import ru.proxodnaya.shift.features.alert.model.AlertCandidate;
import ru.proxodnaya.shift.features.alert.model.AlertType;
import ru.proxodnaya.shift.messaging.outbox.Outbox;

/**
 * Правила оповещений B-01/B-05: одно активное оповещение на условие, пропало условие — оповещение снимается.
 * Новое — в {@code alerts}, outbox {@code shift.alert.v1} и SSE {@code alert.created}.
 */
@Service
public class AlertService {

    private static final List<AlertType> PERSON_TYPES = List.of(AlertType.LEFT_SITE, AlertType.NO_SIGNAL);
    private static final List<AlertType> ZONE_TYPES = List.of(AlertType.ZONE_CROWDED);

    private final AlertRepository repository;
    private final Outbox outbox;
    private final ApplicationEventPublisher events;
    private final ShiftProperties shiftProps;
    private final AlertProperties props;
    private final AlertRules rules;
    private final Clock clock;
    private final Map<String, Instant> crowdedSince = new ConcurrentHashMap<>();

    public AlertService(AlertRepository repository, Outbox outbox, ApplicationEventPublisher events, ShiftProperties shiftProps,
                        AlertProperties props, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.events = events;
        this.shiftProps = shiftProps;
        this.props = props;
        this.clock = clock;
        this.rules = new AlertRules(props.fenceEnabled(), props.noSignalAfter());
    }

    /**
     * Один шаг правил.
     *
     * @return сколько оповещений поднято
     */
    @Transactional
    public int runRules() {
        Instant now = clock.instant();
        Instant fresh = now.minus(shiftProps.presenceTtl());
        int raised = sync(PERSON_TYPES, rules.evaluate(repository.onSiteInShift(now, fresh), now), now);
        return raised + sync(ZONE_TYPES, crowded(repository.insideByZone(fresh), now), now);
    }

    /**
     * Принять оповещение.
     *
     * @param id     id
     * @param userId кто принял
     * @throws NotFoundException если нет такого
     */
    @Transactional
    public void ack(UUID id, String userId) {
        if (!repository.ack(id, userId, clock.instant())) {
            throw new NotFoundException("Оповещение не найдено");
        }
    }

    private List<AlertCandidate> crowded(List<AlertRepository.ZoneCount> zones, Instant now) {
        List<AlertCandidate> found = new ArrayList<>();
        if (props.crowdLimit() <= 0) {
            crowdedSince.clear();
            return found;
        }
        Set<String> over = new HashSet<>();
        for (AlertRepository.ZoneCount zone : zones) {
            if (zone.inside() > props.crowdLimit()) {
                over.add(zone.zoneId());
                Instant since = crowdedSince.computeIfAbsent(zone.zoneId(), key -> now);
                if (!since.plus(props.crowdFor()).isAfter(now)) {
                    found.add(new AlertCandidate(AlertType.ZONE_CROWDED, null, zone.zoneId(), "В зоне " + zone.inside()
                            + " чел. при норме " + props.crowdLimit() + " уже " + Duration.between(since, now).toMinutes() + " мин"));
                }
            }
        }
        crowdedSince.keySet().retainAll(over);
        return found;
    }

    private int sync(List<AlertType> types, List<AlertCandidate> found, Instant now) {
        int raised = 0;
        Set<String> keys = new HashSet<>();
        for (AlertCandidate candidate : found) {
            keys.add(candidate.key());
            if (!repository.activeExists(candidate.key())) {
                raise(candidate, now);
                raised++;
            }
        }
        repository.resolveExcept(types, keys, now);
        return raised;
    }

    private void raise(AlertCandidate candidate, Instant now) {
        UUID id = UUID.randomUUID();
        repository.insert(id, candidate, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", id.toString());
        payload.put("type", candidate.type().name());
        payload.put("severity", candidate.type().severity());
        payload.put("workerId", candidate.workerId());
        payload.put("zoneId", candidate.zoneId());
        payload.put("details", candidate.details());
        outbox.add(Topics.SHIFT_ALERT_V1, "AlertCreated", candidate.workerId() != null ? candidate.workerId() : candidate.zoneId(),
                payload);
        events.publishEvent(new AlertRaised(id, candidate.type().name(), candidate.type().severity(), candidate.workerId(),
                candidate.zoneId(), candidate.details(), now));
    }
}
