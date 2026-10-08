package ru.proxodnaya.access.features.pass.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.access.features.pass.model.DecisionResult;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.PassContext;
import ru.proxodnaya.access.features.qr.model.QrPayload;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты DecisionEngine: по тесту на каждый код отказа (ACC-07).
 * Порядок проверок — fail-fast: первая проваленная = причина.
 */
class DecisionEngineTest {

    private final DecisionEngine engine = new DecisionEngine();

    @Test
    void decide_allOk_returnsAllow() {
        // given
        PassContext ctx = baseOkBuilder().build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(Decision.ALLOW, result.decision());
        assertEquals(ReasonCode.OK, result.reasonCode());
    }

    @Test
    void decide_tempLocked_returnsTempLocked() {
        // given
        PassContext ctx = baseOkBuilder().recentFailures(5).build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.TEMP_LOCKED, result.reasonCode());
    }

    @Test
    void decide_workerBlocked_returnsWorkerBlocked() {
        // given
        PassContext ctx = baseOkBuilder().workerStatus("BLOCKED").build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.WORKER_BLOCKED, result.reasonCode());
    }

    @Test
    void decide_livenessFailed_returnsLivenessFailed() {
        // given
        FaceResult face = new FaceResult(false, 0.5, false, 0.8, "LIVENESS_FAILED");
        PassContext ctx = baseOkBuilder().faceResult(face).build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.LIVENESS_FAILED, result.reasonCode());
    }

    @Test
    void decide_faceLowQuality_returnsFaceLowQuality() {
        // given — liveness ok, quality low
        FaceResult face = new FaceResult(false, 0.5, true, 0.2, "FACE_LOW_QUALITY");
        PassContext ctx = baseOkBuilder().faceResult(face).build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.FACE_LOW_QUALITY, result.reasonCode());
    }

    @Test
    void decide_faceMismatch_returnsFaceMismatch() {
        // given — liveness ok, quality ok, but not matching
        FaceResult face = new FaceResult(false, 0.4, true, 0.8, "FACE_MISMATCH");
        PassContext ctx = baseOkBuilder().faceResult(face).build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.FACE_MISMATCH, result.reasonCode());
    }

    @Test
    void decide_noShift_returnsNoShift() {
        // given
        PassContext ctx = baseOkBuilder().shiftActive(false).build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.NO_SHIFT, result.reasonCode());
    }

    @Test
    void decide_outsideShiftWindow_returnsOutsideShiftWindow() {
        // given
        PassContext ctx = baseOkBuilder().shiftWindowOk(false).build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.OUTSIDE_SHIFT_WINDOW, result.reasonCode());
    }

    @Test
    void decide_permitExpired_returnsPermitExpired() {
        // given
        PassContext ctx = baseOkBuilder()
                .permitUntil(LocalDate.now().minusDays(1))
                .build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.PERMIT_EXPIRED, result.reasonCode());
    }

    @Test
    void decide_noZonePermit_returnsNoZonePermit() {
        // given
        PassContext ctx = baseOkBuilder()
                .zoneId("z_restricted")
                .workerZoneIds(Set.of("z_a", "z_b"))
                .build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.NO_ZONE_PERMIT, result.reasonCode());
    }

    @Test
    void decide_alreadyInside_returnsAlreadyInside() {
        // given
        PassContext ctx = baseOkBuilder()
                .direction(Direction.IN)
                .isInside(true)
                .build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.ALREADY_INSIDE, result.reasonCode());
    }

    @Test
    void decide_notInside_returnsNotInside() {
        // given
        PassContext ctx = baseOkBuilder()
                .direction(Direction.OUT)
                .isInside(false)
                .build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.NOT_INSIDE, result.reasonCode());
    }

    @Test
    void decide_zoneFull_returnsZoneFull() {
        // given
        PassContext ctx = baseOkBuilder()
                .direction(Direction.IN)
                .zoneOccupancy(50)
                .zoneCapacity(50)
                .build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(ReasonCode.ZONE_FULL, result.reasonCode());
    }

    @Test
    void decide_allowWithFaceScore_returnsScore() {
        // given
        FaceResult face = FaceResult.ok(0.85, 0.9);
        PassContext ctx = baseOkBuilder().faceResult(face).build();

        // when
        DecisionResult result = engine.decide(ctx);

        // then
        assertEquals(Decision.ALLOW, result.decision());
        assertEquals(0.85, result.faceScore());
    }

    /**
     * Базовый builder с валидным контекстом для всех проверок OK.
     * Каждый тест переопределяет нужное поле.
     */
    private static class PassContextBuilder {
        private UUID attemptId = UUID.randomUUID();
        private QrPayload qr = new QrPayload("dev_001", 1000L, new byte[]{1});
        private Direction direction = Direction.IN;
        private String checkpointId = "cp_main";
        private String zoneId = "z_a";
        private byte[] devicePublicKey = new byte[]{};
        private String workerId = "w_01";
        private String workerStatus = "ACTIVE";
        private LocalDate permitUntil = LocalDate.now().plusDays(90);
        private Set<String> workerZoneIds = Set.of("z_a", "z_b");
        private FaceResult faceResult = null;
        private boolean shiftActive = true;
        private boolean shiftWindowOk = true;
        private boolean isInside = false;
        private int zoneOccupancy = 5;
        private int zoneCapacity = 40;
        private int recentFailures = 0;
        private Instant now = Instant.now();

        PassContextBuilder direction(Direction v) { this.direction = v; return this; }
        PassContextBuilder workerStatus(String v) { this.workerStatus = v; return this; }
        PassContextBuilder permitUntil(LocalDate v) { this.permitUntil = v; return this; }
        PassContextBuilder workerZoneIds(Set<String> v) { this.workerZoneIds = v; return this; }
        PassContextBuilder zoneId(String v) { this.zoneId = v; return this; }
        PassContextBuilder faceResult(FaceResult v) { this.faceResult = v; return this; }
        PassContextBuilder shiftActive(boolean v) { this.shiftActive = v; return this; }
        PassContextBuilder shiftWindowOk(boolean v) { this.shiftWindowOk = v; return this; }
        PassContextBuilder isInside(boolean v) { this.isInside = v; return this; }
        PassContextBuilder zoneOccupancy(int v) { this.zoneOccupancy = v; return this; }
        PassContextBuilder zoneCapacity(int v) { this.zoneCapacity = v; return this; }
        PassContextBuilder recentFailures(int v) { this.recentFailures = v; return this; }

        PassContext build() {
            return new PassContext(
                    attemptId, qr, direction, checkpointId, zoneId,
                    devicePublicKey, workerId, workerStatus, permitUntil, workerZoneIds,
                    faceResult, shiftActive, shiftWindowOk, isInside,
                    zoneOccupancy, zoneCapacity, recentFailures, now
            );
        }
    }

    private PassContextBuilder baseOkBuilder() {
        return new PassContextBuilder();
    }
}
