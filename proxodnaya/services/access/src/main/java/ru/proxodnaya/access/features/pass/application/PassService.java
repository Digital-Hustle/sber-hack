package ru.proxodnaya.access.features.pass.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import ru.proxodnaya.access.features.pass.command.service.ChallengeService;
import ru.proxodnaya.access.features.pass.gateway.FaceGateway;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.gateway.ShiftGateway;
import ru.proxodnaya.access.features.pass.model.ActiveShift;
import ru.proxodnaya.access.features.pass.model.DecisionResult;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.PassContext;
import ru.proxodnaya.access.features.pass.model.PassOutcome;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.pass.model.PendingAttempt;
import ru.proxodnaya.access.features.pass.model.ScanOutcome;
import ru.proxodnaya.access.features.pass.model.WorkerPreview;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;
import ru.proxodnaya.access.features.pass.query.service.PassQueryService;
import ru.proxodnaya.access.features.qr.api.QrCheck;
import ru.proxodnaya.access.features.qr.api.QrCheckResult;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.ChallengeAction;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.common.exception.NotFoundException;

/**
 * Поток прохода (FR-01, FR-06, NFR-16).
 *
 * <ol>
 *   <li>Скан: QR локально (подпись, окно, ключ из проекции) → блокировка, TEMP_LOCKED, REPEAT_SCAN →
 *       направление по присутствию → профиль people → челлендж.</li>
 *   <li>Кадры: face, people, shift параллельно с таймаутом (fail-closed) → DecisionEngine →
 *       одна транзакция: гашение QR + журнал + присутствие + outbox.</li>
 * </ol>
 */
@Service
public class PassService {

    private static final String FACE = "face";
    private static final String PEOPLE = "people";
    private static final String SHIFT = "shift";
    private static final String FAILED = "failedDependency";
    private static final ChallengeAction[] ACTIONS = ChallengeAction.values();

    private final QrCheck qrCheck;
    private final PassQueryService query;
    private final ChallengeService challenges;
    private final FaceGateway face;
    private final PeopleGateway people;
    private final ShiftGateway shift;
    private final DecisionEngine engine;
    private final PassRecorder recorder;
    private final AccessProperties props;
    private final Clock clock;
    private final ExecutorService executor;
    private final SecureRandom random = new SecureRandom();

    public PassService(QrCheck qrCheck, PassQueryService query, ChallengeService challenges, FaceGateway face,
                       PeopleGateway people, ShiftGateway shift, DecisionEngine engine, PassRecorder recorder,
                       AccessProperties props, Clock clock, @Qualifier("depsExecutor") ExecutorService executor) {
        this.qrCheck = qrCheck;
        this.query = query;
        this.challenges = challenges;
        this.face = face;
        this.people = people;
        this.shift = shift;
        this.engine = engine;
        this.recorder = recorder;
        this.props = props;
        this.clock = clock;
        this.executor = executor;
    }

    /**
     * Шаг 1: скан QR.
     *
     * @param rawQr        строка QR
     * @param checkpointId проходная
     * @return отказ или челлендж
     */
    public ScanOutcome scan(String rawQr, String checkpointId) {
        Instant now = now();
        UUID id = UuidCreator.getTimeOrderedEpoch();
        QrCheckResult qrResult = qrCheck.check(rawQr, now);
        if (!qrResult.isOk()) {
            return ScanOutcome.decided(denyEarly(id, now, checkpointId, null, Direction.IN, qrResult, qrResult.code(),
                    Map.of(), null));
        }
        String workerId = qrResult.workerId();
        Direction direction = query.direction(workerId, now);
        Map<String, Object> details = new LinkedHashMap<>();
        Optional<WorkerProfile> profile = Optional.empty();
        String zoneId = null;
        ReasonCode code = preCheck(workerId, now);
        if (code == ReasonCode.OK) {
            CompletableFuture<Optional<WorkerProfile>> profileF = async(() -> people.accessProfile(workerId));
            CompletableFuture<Optional<String>> zoneF = async(() -> people.checkpointZone(checkpointId));
            try {
                zoneId = zoneF.join()
                        .orElseThrow(() -> new NotFoundException("Проходная не найдена — проверьте настройку терминала"));
                profile = profileF.join();
                code = profileCode(profile, qrResult.deviceId());
            } catch (CompletionException error) {
                code = ReasonCode.SYSTEM_ERROR;
                details.put(FAILED, List.of(PEOPLE));
            }
        }
        WorkerPreview preview = profile.map(WorkerPreview::of).orElse(null);
        if (code != ReasonCode.OK) {
            return ScanOutcome.decided(denyEarly(id, now, checkpointId, zoneId, direction, qrResult, code, details,
                    preview));
        }
        PendingAttempt attempt = new PendingAttempt(id, workerId, qrResult.deviceId(), qrResult.window(), direction, checkpointId,
                zoneId, ACTIONS[random.nextInt(ACTIONS.length)], props.framesRequired(), now, now.plus(props.challengeTtl()));
        challenges.create(attempt);
        return ScanOutcome.challenge(attempt, preview);
    }

    /**
     * Шаг 2: кадры челленджа.
     *
     * @param token  токен из ответа на скан
     * @param frames кадры JPEG base64
     * @return решение
     */
    public PassOutcome submitFrames(UUID token, List<String> frames) {
        Instant now = now();
        PendingAttempt pending = challenges.take(token)
                .orElseThrow(() -> new NotFoundException("Попытка не найдена или уже завершена"));
        if (now.isAfter(pending.expiresAt()) || frames.size() < pending.framesRequired()) {
            ReasonCode code = now.isAfter(pending.expiresAt()) ? ReasonCode.CHALLENGE_EXPIRED : ReasonCode.LIVENESS_FAILED;
            PassRecord rec = record(pending, now, DecisionResult.deny(code, null), null, Map.of("framesReceived", frames.size()));
            return outcome(recorder.deny(rec), null);
        }
        CompletableFuture<FaceResult> faceF = async(() -> face.verify(pending.workerId(), frames, pending.action()));
        CompletableFuture<Optional<WorkerProfile>> profileF = async(() -> people.accessProfile(pending.workerId()));
        CompletableFuture<Optional<ActiveShift>> shiftF = pending.direction() == Direction.IN
                ? async(() -> shift.activeShift(pending.workerId(), now))
                : CompletableFuture.completedFuture(Optional.empty());
        List<String> failed = new ArrayList<>();
        FaceResult faceResult = join(faceF, FACE, failed);
        Optional<WorkerProfile> profile = Optional.ofNullable(join(profileF, PEOPLE, failed)).flatMap(opt -> opt);
        Optional<ActiveShift> activeShift = Optional.ofNullable(join(shiftF, SHIFT, failed)).flatMap(opt -> opt);
        PassContext ctx = new PassContext(pending.direction(), pending.deviceId(), pending.zoneId(), profile.orElse(null), faceResult,
                activeShift.orElse(null), failed.isEmpty() ? null : String.join(",", failed),
                LocalDate.ofInstant(now, props.siteZone()));
        DecisionResult decision = engine.decide(ctx);
        Map<String, Object> details = new LinkedHashMap<>();
        if (!failed.isEmpty()) {
            details.put(FAILED, failed);
        }
        if (faceResult != null) {
            details.put("faceQuality", faceResult.quality());
            details.put("faceReason", faceResult.reason());
        }
        PassRecord saved = recorder.finish(record(pending, now, decision, faceResult, details));
        return outcome(saved, profile.map(WorkerPreview::of).orElse(null));
    }

    private static ReasonCode profileCode(Optional<WorkerProfile> profile, String deviceId) {
        ReasonCode code = ReasonCode.OK;
        if (profile.isEmpty() || !profile.get().isActive()) {
            code = ReasonCode.WORKER_BLOCKED;
        } else if (!profile.get().deviceIds().contains(deviceId)) {
            code = ReasonCode.DEVICE_MISMATCH;
        }
        return code;
    }

    private ReasonCode preCheck(String workerId, Instant now) {
        ReasonCode code = ReasonCode.OK;
        if (query.isBlocked(workerId)) {
            code = ReasonCode.WORKER_BLOCKED;
        } else if (query.faceFailuresSince(workerId, now.minus(props.tempLockWindow())) >= props.tempLockFailures()) {
            code = ReasonCode.TEMP_LOCKED;
        } else if (query.lastAllowAt(workerId).filter(last -> last.isAfter(now.minus(props.repeatScanCooldown()))).isPresent()) {
            code = ReasonCode.REPEAT_SCAN;
        }
        return code;
    }

    private PassOutcome denyEarly(UUID id, Instant now, String checkpointId, String zoneId, Direction direction, QrCheckResult qrResult,
                                  ReasonCode code, Map<String, Object> details, WorkerPreview worker) {
        PassRecord rec = new PassRecord(id, now, checkpointId, zoneId, direction, qrResult.workerId(), qrResult.deviceId(),
                qrResult.deviceId() == null ? null : qrResult.window(), null, null, null, Decision.DENY, code,
                latency(now), AttemptSource.KIOSK, details);
        return outcome(recorder.deny(rec), worker);
    }

    private PassRecord record(PendingAttempt pending, Instant now, DecisionResult verdict, FaceResult faceResult,
                              Map<String, Object> details) {
        return new PassRecord(pending.token(), now, pending.checkpointId(), pending.zoneId(), pending.direction(), pending.workerId(),
                pending.deviceId(), pending.window(),
                verdict.score(), faceResult == null ? null : faceResult.liveness(), pending.action(), verdict.decision(), verdict.code(),
                latency(now), AttemptSource.KIOSK, details);
    }

    private static PassOutcome outcome(PassRecord rec, WorkerPreview worker) {
        return new PassOutcome(rec.id(), rec.decision(), rec.code(), worker, rec.direction(), rec.faceScore(), rec.ts());
    }

    private <T> CompletableFuture<T> async(Supplier<T> call) {
        return CompletableFuture.supplyAsync(call, executor).orTimeout(props.deps().timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private static <T> T join(CompletableFuture<T> future, String name, List<String> failed) {
        try {
            return future.join();
        } catch (CompletionException error) {
            failed.add(name);
            return null;
        }
    }

    private long latency(Instant start) {
        return Math.max(0, Duration.between(start, clock.instant()).toMillis());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
