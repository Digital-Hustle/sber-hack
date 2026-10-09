package ru.proxodnaya.access.features.pass.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.github.f4b6a3.uuid.UuidCreator;

import ru.proxodnaya.access.config.properties.AccessProperties;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.model.PassOutcome;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.pass.model.WorkerPreview;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;
import ru.proxodnaya.access.features.pass.query.service.PassQueryService;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.common.exception.NotFoundException;

/**
 * Ручной проход охранника (FR-14): override с обязательной причиной, {@code decision = MANUAL}, {@code code = MANUAL_GUARD},
 * {@code source = MANUAL}. Кто пропустил и почему — в {@code details} записи журнала.
 *
 * <p>Override снимает проверки QR, лица и смены, но не блокировку: заблокированного (или удалённого) рабочего
 * охранник не пропустит — {@code WORKER_BLOCKED}. Направление выводит сервер по присутствию (ADR-037).
 * people недоступен — fail-closed, {@code SYSTEM_ERROR} в журнале.</p>
 */
@Service
public class ManualPassService {

    static final String NOTE = "note";
    static final String GUARD = "guardId";
    private static final String FAILED = "failedDependency";

    private final PeopleGateway people;
    private final PassQueryService query;
    private final PassRecorder recorder;
    private final AccessProperties props;
    private final Clock clock;
    private final ExecutorService executor;

    public ManualPassService(PeopleGateway people, PassQueryService query, PassRecorder recorder, AccessProperties props,
                             Clock clock, @Qualifier("depsExecutor") ExecutorService executor) {
        this.people = people;
        this.query = query;
        this.recorder = recorder;
        this.props = props;
        this.clock = clock;
        this.executor = executor;
    }

    /**
     * Пропускает рабочего вручную.
     *
     * @param workerId     кого пропускают
     * @param checkpointId проходная киоска
     * @param note         причина (обязательна)
     * @param guardId      охранник из токена
     * @return итог: MANUAL_GUARD, WORKER_BLOCKED или SYSTEM_ERROR
     * @throws NotFoundException проходная или рабочий не найдены (в журнал не пишется)
     */
    public PassOutcome pass(String workerId, String checkpointId, String note, String guardId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CompletableFuture<Optional<String>> zoneF = async(() -> people.checkpointZone(checkpointId));
        CompletableFuture<Optional<WorkerProfile>> profileF = async(() -> people.accessProfile(workerId));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(NOTE, note.strip());
        details.put(GUARD, guardId);
        Optional<String> zone;
        Optional<WorkerProfile> profile;
        try {
            zone = zoneF.join();
            profile = profileF.join();
        } catch (CompletionException error) {
            details.put(FAILED, List.of("people"));
            return deny(workerId, checkpointId, null, now, ReasonCode.SYSTEM_ERROR, details, null);
        }
        String zoneId = zone.orElseThrow(() -> new NotFoundException("Проходная не найдена — проверьте настройку терминала"));
        WorkerProfile worker = profile.orElseThrow(() -> new NotFoundException("Сотрудник не найден"));
        WorkerPreview preview = WorkerPreview.of(worker);
        if (!worker.isActive() || query.isBlocked(workerId)) {
            return deny(workerId, checkpointId, zoneId, now, ReasonCode.WORKER_BLOCKED, details, preview);
        }
        PassRecord saved = recorder.manual(record(workerId, checkpointId, zoneId, now, Decision.MANUAL, ReasonCode.MANUAL_GUARD,
                details));
        return outcome(saved, preview);
    }

    private PassOutcome deny(String workerId, String checkpointId, String zoneId, Instant now, ReasonCode code,
                             Map<String, Object> details, WorkerPreview preview) {
        return outcome(recorder.deny(record(workerId, checkpointId, zoneId, now, Decision.DENY, code, details)), preview);
    }

    private PassRecord record(String workerId, String checkpointId, String zoneId, Instant now, Decision decision, ReasonCode code,
                              Map<String, Object> details) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        return new PassRecord(id, now, checkpointId, zoneId, query.direction(workerId, now), workerId, null, null, null, null, null,
                decision, code, latency(now), AttemptSource.MANUAL, details);
    }

    private static PassOutcome outcome(PassRecord rec, WorkerPreview worker) {
        return new PassOutcome(rec.id(), rec.decision(), rec.code(), worker, rec.direction(), rec.faceScore(), rec.ts());
    }

    private <T> CompletableFuture<T> async(Supplier<T> call) {
        return CompletableFuture.supplyAsync(call, executor).orTimeout(props.deps().timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private long latency(Instant start) {
        return Math.max(0, Duration.between(start, clock.instant()).toMillis());
    }
}
