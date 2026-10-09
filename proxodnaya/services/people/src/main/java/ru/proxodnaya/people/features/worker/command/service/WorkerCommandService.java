package ru.proxodnaya.people.features.worker.command.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.common.event.Topics;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.site.api.ZoneDirectory;
import ru.proxodnaya.people.features.worker.command.repository.WorkerAccessRepository;
import ru.proxodnaya.people.features.worker.command.repository.WorkerRepository;
import ru.proxodnaya.people.features.worker.model.Permit;
import ru.proxodnaya.people.features.worker.model.PermitType;
import ru.proxodnaya.people.features.worker.model.Worker;
import ru.proxodnaya.people.features.worker.model.WorkerDraft;
import ru.proxodnaya.people.messaging.outbox.Outbox;
import ru.proxodnaya.people.shared.WorkerStatus;

/**
 * Изменения рабочих (PPL-02). Каждое изменение и событие {@code people.worker.v1} — в одной транзакции.
 */
@Service
public class WorkerCommandService {

    private static final String UPDATED = "WorkerUpdated";

    private final WorkerRepository workers;
    private final WorkerAccessRepository access;
    private final ZoneDirectory zones;
    private final Outbox outbox;
    private final Clock clock;

    public WorkerCommandService(WorkerRepository workers, WorkerAccessRepository access, ZoneDirectory zones,
                                Outbox outbox, Clock clock) {
        this.workers = workers;
        this.access = access;
        this.zones = zones;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Создаёт рабочего.
     *
     * @param draft данные (все поля обязательны)
     * @return рабочий
     */
    @Transactional
    public Worker create(WorkerDraft draft) {
        Instant now = clock.instant();
        Worker worker = new Worker(UuidCreator.getTimeOrderedEpoch(), clean(draft.fullName()), clean(draft.position()),
                clean(draft.contractor()), WorkerStatus.ACTIVE, now, now);
        workers.insert(worker);
        access.replaceZones(worker.id(), checkedZones(draft.zoneIds() == null ? Set.of() : draft.zoneIds()));
        access.replacePermits(worker.id(), checkedPermits(draft.permits() == null ? List.of() : draft.permits()));
        publish("WorkerCreated", worker);
        return worker;
    }

    /**
     * Меняет карточку рабочего.
     *
     * @param id    рабочий
     * @param draft изменения
     * @return новое состояние
     */
    @Transactional
    public Worker update(UUID id, WorkerDraft draft) {
        Worker current = load(id);
        WorkerDraft cleaned = new WorkerDraft(cleanOrNull(draft.fullName()), cleanOrNull(draft.position()),
                cleanOrNull(draft.contractor()), draft.zoneIds(), draft.permits());
        Worker updated = WorkerRepository.apply(current, cleaned, clock.instant());
        workers.update(updated);
        if (draft.zoneIds() != null) {
            access.replaceZones(id, checkedZones(draft.zoneIds()));
        }
        if (draft.permits() != null) {
            access.replacePermits(id, checkedPermits(draft.permits()));
        }
        publish(UPDATED, updated);
        return updated;
    }

    /**
     * Блокирует рабочего: access отказывает со следующего прохода.
     *
     * @param id рабочий
     * @return новое состояние
     */
    @Transactional
    public Worker block(UUID id) {
        return changeStatus(id, WorkerStatus.BLOCKED, "WorkerBlocked");
    }

    /**
     * Снимает блокировку.
     *
     * @param id рабочий
     * @return новое состояние
     */
    @Transactional
    public Worker unblock(UUID id) {
        return changeStatus(id, WorkerStatus.ACTIVE, UPDATED);
    }

    /**
     * Удаляет рабочего (мягко). Каскад в face — PPL-10.
     *
     * @param id рабочий
     */
    @Transactional
    public void delete(UUID id) {
        changeStatus(id, WorkerStatus.DELETED, "WorkerDeleted");
    }

    private Worker changeStatus(UUID id, WorkerStatus status, String event) {
        Worker current = load(id);
        if (current.status() == status) {
            return current;
        }
        Worker updated = new Worker(current.id(), current.fullName(), current.position(), current.contractor(), status,
                current.createdAt(), clock.instant());
        workers.update(updated);
        publish(event, updated);
        return updated;
    }

    private Worker load(UUID id) {
        return workers.lock(id).orElseThrow(() -> new DomainException(ErrorCode.WORKER_NOT_FOUND, "Рабочий не найден"));
    }

    private void publish(String type, Worker worker) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workerId", worker.id().toString());
        payload.put("status", worker.status().name());
        payload.put("fullName", worker.fullName());
        payload.put("position", worker.position());
        payload.put("contractor", worker.contractor());
        outbox.add(Topics.PEOPLE_WORKER_V1, type, worker.id().toString(), payload);
    }

    private Set<UUID> checkedZones(Set<UUID> zoneIds) {
        if (!zones.missing(zoneIds).isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Зона не найдена");
        }
        return zoneIds;
    }

    private static List<Permit> checkedPermits(List<Permit> permits) {
        Set<PermitType> types = new HashSet<>();
        for (Permit permit : permits) {
            if (!types.add(permit.type())) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Каждый вид допуска — один раз");
            }
        }
        return permits;
    }

    private static String clean(String value) {
        String cleaned = cleanOrNull(value);
        if (cleaned == null) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Заполните ФИО, должность и подрядчика");
        }
        return cleaned;
    }

    private static String cleanOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Поле не может быть пустым");
        }
        return trimmed;
    }
}
