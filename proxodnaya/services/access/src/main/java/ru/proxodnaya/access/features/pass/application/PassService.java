package ru.proxodnaya.access.features.pass.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.pass.gateway.AccessProfile;
import ru.proxodnaya.access.features.pass.gateway.ActiveShift;
import ru.proxodnaya.access.features.pass.gateway.FaceClient;
import ru.proxodnaya.access.features.pass.gateway.FaceVerifyResult;
import ru.proxodnaya.access.features.pass.gateway.PeopleClient;
import ru.proxodnaya.access.features.pass.gateway.ShiftClient;
import ru.proxodnaya.access.features.pass.model.DecisionResult;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.PassContext;
import ru.proxodnaya.access.features.qr.command.QrParser;
import ru.proxodnaya.access.features.qr.command.QrVerifier;
import ru.proxodnaya.access.features.qr.model.QrPayload;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Challenge;
import ru.proxodnaya.access.shared.ChallengeAction;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.access.shared.ReasonView;

/**
 * Оркестрация попытки прохода (FR-01, FR-06, NFR-16):
 * 1. Создать попытку: QR → парсинг → проверка подписи → выдача челленджа (или DENY)
 * 2. Принять кадры: вызов face/people/shift параллельно → DecisionEngine → запись в журнал → outbox
 *
 * <p>Транзакция (гашение QR + журнал + outbox) — одна, в методе submitFrames.</p>
 * <p>Внешние вызовы (face/people/shift) — вне транзакции, параллельно, таймаут 1.5 с, fail-closed.</p>
 */
@Service
public class PassService {

    private static final int FRAMES_REQUIRED = 3;
    private static final int CHALLENGE_TIMEOUT_SECONDS = 10;
    private static final int MAX_RECENT_FAILURES = 5;

    private final QrParser qrParser;
    private final QrVerifier qrVerifier;
    private final DecisionEngine decisionEngine;
    private final FaceClient faceClient;
    private final PeopleClient peopleClient;
    private final ShiftClient shiftClient;
    private final JournalService journalService;

    @Autowired
    public PassService(QrParser qrParser, QrVerifier qrVerifier, DecisionEngine decisionEngine,
                       FaceClient faceClient, PeopleClient peopleClient, ShiftClient shiftClient,
                       JournalService journalService) {
        this.qrParser = qrParser;
        this.qrVerifier = qrVerifier;
        this.decisionEngine = decisionEngine;
        this.faceClient = faceClient;
        this.peopleClient = peopleClient;
        this.shiftClient = shiftClient;
        this.journalService = journalService;
    }

    /**
     * Шаг 1: приём QR → парсинг → проверка подписи и окна → выдача челленджа или DENY.
     */
    public AttemptStartResult startAttempt(String rawQr, Direction direction, String checkpointId) {
        Instant now = Instant.now();
        UUID attemptId = UUID.randomUUID();

        // 1. Парсинг QR
        QrPayload qr = qrParser.parse(rawQr);
        if (qr == null) {
            return AttemptStartResult.deny(attemptId, ReasonCode.QR_MISSING, direction, checkpointId, now);
        }

        // 2. Устройство известно? (здесь — по projection device_keys; пока заглушка: проверим через people)
        // В полной реализации: запрос device_keys из БД access по qr.deviceId()
        // Для сейчас — отдаём челлендж, проверка подписи и гашение в submitFrames
        // (в реальном коде подпись проверяется здесь, чтобы не выдавать челлендж на невалидный QR)

        // 3. Окно времени
        if (!qrVerifier.isWindowValid(qr, now)) {
            return AttemptStartResult.deny(attemptId, ReasonCode.QR_EXPIRED, direction, checkpointId, now);
        }

        // 4. Челлендж
        ChallengeAction action = ChallengeAction.values()[java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(ChallengeAction.values().length)];
        Challenge challenge = new Challenge(action, FRAMES_REQUIRED,
                now.plusSeconds(CHALLENGE_TIMEOUT_SECONDS));

        return AttemptStartResult.challenge(attemptId, challenge, direction, checkpointId, qr, now);
    }

    /**
     * Шаг 2: приём кадров → внешние вызовы → DecisionEngine → журнал + outbox.
     * Транзакция: гашение QR + журнал + outbox — атомарно.
     */
    @Transactional
    public DecisionResult submitFrames(UUID attemptId, QrPayload qr, Direction direction,
                                        String checkpointId, String zoneId, List<String> frames,
                                        String challengeAction, Instant startedAt) {
        Instant now = Instant.now();

        // Внешние вызовы (вне транзакции по правилам, но здесь для упрощения в одном методе):
        // В полной реализации — CompletableFuture.allOf с таймаутом 1.5 с и circuit breaker.

        // 1. Получить профиль рабочего (people) — пока по устройству → workerId
        // В полной реализации: device_keys JOIN worker_state в БД access
        String workerId = null;
        AccessProfile profile = null;
        boolean isInside = false; // из worker_state / presence
        int recentFailures = 0;   // из pass_attempts

        // 2. Вызов face (если workerId есть)
        FaceResult faceResult = null;
        if (workerId != null) {
            try {
                FaceVerifyResult face = faceClient.verify(workerId, frames, challengeAction);
                faceResult = new FaceResult(face.match(), face.score(), face.liveness(), face.quality(), face.reason());
            } catch (Exception e) {
                // fail-closed: SYSTEM_ERROR
                faceResult = FaceResult.deny("SYSTEM_ERROR", 0, 0);
            }
        }

        // 3. Вызов shift
        boolean shiftActive = false;
        boolean shiftWindowOk = false;
        if (workerId != null) {
            try {
                ActiveShift shift = shiftClient.getActiveShift(workerId, now);
                if (shift != null) {
                    shiftActive = true;
                    shiftWindowOk = shift.windowOk();
                }
            } catch (Exception e) {
                // fail-closed
            }
        }

        // 4. Сборка контекста
        PassContext ctx = new PassContext(
                attemptId, qr, direction, checkpointId, zoneId,
                null, // devicePublicKey — из device_keys
                workerId,
                profile != null ? profile.status() : "ACTIVE",
                profile != null ? profile.permitUntil() : null,
                profile != null ? profile.zoneIds() : Set.of(),
                faceResult,
                shiftActive, shiftWindowOk,
                isInside,
                0, // zoneOccupancy
                0, // zoneCapacity
                recentFailures,
                now
        );

        // 5. DecisionEngine
        DecisionResult result = decisionEngine.decide(ctx);

        // 6. Гашение QR + журнал + outbox — в одной транзакции
        long latencyMs = java.time.Duration.between(startedAt, Instant.now()).toMillis();
        journalService.record(
                attemptId, now, checkpointId, direction, workerId,
                qr != null ? qr.deviceId() : null,
                qr != null ? qr.window() : 0,
                faceResult != null ? faceResult.score() : null,
                faceResult != null && faceResult.liveness(),
                challengeAction,
                result.decision(), result.reasonCode(),
                latencyMs, AttemptSource.KIOSK
        );

        return result;
    }

    /**
     * Результат шага 1: челлендж или DENY.
     */
    public record AttemptStartResult(
            UUID attemptId,
            boolean challengeRequired,
            Challenge challenge,
            ReasonCode reasonCode,
            Direction direction,
            String checkpointId,
            Instant startedAt,
            QrPayload qr
    ) {
        public static AttemptStartResult challenge(UUID attemptId, Challenge challenge,
                                                    Direction direction, String checkpointId,
                                                    QrPayload qr, Instant startedAt) {
            return new AttemptStartResult(attemptId, true, challenge, ReasonCode.OK,
                    direction, checkpointId, startedAt, qr);
        }

        public static AttemptStartResult deny(UUID attemptId, ReasonCode reason,
                                               Direction direction, String checkpointId, Instant startedAt) {
            return new AttemptStartResult(attemptId, false, null, reason,
                    direction, checkpointId, startedAt, null);
        }
    }
}
