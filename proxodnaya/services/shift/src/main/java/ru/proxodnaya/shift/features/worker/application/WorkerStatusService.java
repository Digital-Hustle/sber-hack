package ru.proxodnaya.shift.features.worker.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.interval.api.Presence;
import ru.proxodnaya.shift.features.interval.api.PresenceLookup;
import ru.proxodnaya.shift.features.shift.api.ActiveShift;
import ru.proxodnaya.shift.features.shift.api.ShiftLookup;
import ru.proxodnaya.shift.features.worker.model.WorkerStatus;

/**
 * «Мой статус» в телефоне рабочего: на объекте ли, смена, отработано сегодня.
 */
@Service
public class WorkerStatusService {

    private final PresenceLookup presence;
    private final ShiftLookup shifts;
    private final ShiftProperties props;
    private final Clock clock;

    public WorkerStatusService(PresenceLookup presence, ShiftLookup shifts, ShiftProperties props, Clock clock) {
        this.presence = presence;
        this.shifts = shifts;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Статус рабочего.
     *
     * @param workerId рабочий из токена
     * @return статус
     */
    @Transactional(readOnly = true)
    public WorkerStatus status(String workerId) {
        Instant now = clock.instant();
        ZoneId zone = props.siteZone();
        LocalDate today = now.atZone(zone).toLocalDate();
        Optional<Presence> current = presence.current(workerId, now);
        ActiveShift shift = shifts.activeShift(workerId, now).orElse(null);
        Duration worked = presence.worked(workerId, today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant(),
                now);
        return new WorkerStatus(current.isPresent(), current.map(Presence::zoneId).orElse(null),
                current.map(Presence::since).orElse(null), shift, worked);
    }
}
