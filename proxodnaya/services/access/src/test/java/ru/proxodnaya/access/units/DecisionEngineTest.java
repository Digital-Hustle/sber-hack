package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ru.proxodnaya.access.features.pass.application.DecisionEngine;
import ru.proxodnaya.access.features.pass.model.ActiveShift;
import ru.proxodnaya.access.features.pass.model.DecisionResult;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.PassContext;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.access.support.TestProps;

class DecisionEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final FaceResult FACE_OK = new FaceResult(true, 0.91, true, 0.8, null);
    private static final ActiveShift SHIFT_OK = new ActiveShift("s1", Instant.EPOCH, Instant.EPOCH, true);

    private final DecisionEngine engine = new DecisionEngine(TestProps.tuning());

    @Test
    void decide_allChecksPass_allow() {
        // when
        DecisionResult result = engine.decide(ctx(Direction.IN, profile("ACTIVE", TODAY), FACE_OK, SHIFT_OK, null));

        // then
        assertThat(result.decision()).isEqualTo(Decision.ALLOW);
        assertThat(result.code()).isEqualTo(ReasonCode.OK);
        assertThat(result.score()).isEqualTo(0.91);
    }

    @Test
    void decide_failedDependencyOrNoFace_systemErrorFailClosed() {
        // when / then
        assertThat(engine.decide(ctx(Direction.IN, profile("ACTIVE", TODAY), FACE_OK, SHIFT_OK, "face")).code())
                .isEqualTo(ReasonCode.SYSTEM_ERROR);
        assertThat(engine.decide(ctx(Direction.IN, profile("ACTIVE", TODAY), null, SHIFT_OK, null)).code())
                .isEqualTo(ReasonCode.SYSTEM_ERROR);
    }

    @Test
    void decide_noProfileOrBlocked_workerBlocked() {
        // when / then
        assertThat(engine.decide(ctx(Direction.IN, null, FACE_OK, SHIFT_OK, null)).code()).isEqualTo(ReasonCode.WORKER_BLOCKED);
        assertThat(engine.decide(ctx(Direction.IN, profile("BLOCKED", TODAY), FACE_OK, SHIFT_OK, null)).code())
                .isEqualTo(ReasonCode.WORKER_BLOCKED);
    }

    @Test
    void decide_deviceNotInProfile_deviceMismatch() {
        // given
        WorkerProfile profile = new WorkerProfile("w1", "ACTIVE", "Иван", null, null, Set.of("other"), TODAY, Set.of());

        // when / then
        assertThat(engine.decide(ctx(Direction.IN, profile, FACE_OK, SHIFT_OK, null)).code()).isEqualTo(ReasonCode.DEVICE_MISMATCH);
    }

    @ParameterizedTest
    @CsvSource({
        "true,  true,  0.8,  FACE_NOT_FOUND,    FACE_NOT_FOUND",
        "true,  true,  0.8,  FACE_MULTIPLE,     FACE_MULTIPLE",
        "false, true,  0.8,  NOT_ENROLLED,      FACE_NOT_ENROLLED",
        "true,  true,  0.1,  ,                  FACE_LOW_QUALITY",
        "false, true,  0.8,  ,                  FACE_MISMATCH",
        "true,  false, 0.8,  ,                  LIVENESS_FAILED"
    })
    void decide_faceProblems_mappedInOrder(boolean match, boolean liveness, double quality, String reason, ReasonCode expected) {
        // given
        FaceResult face = new FaceResult(match, 0.4, liveness, quality, reason);

        // when
        DecisionResult result = engine.decide(ctx(Direction.IN, profile("ACTIVE", TODAY), face, SHIFT_OK, null));

        // then
        assertThat(result.decision()).isEqualTo(Decision.DENY);
        assertThat(result.code()).isEqualTo(expected);
        assertThat(result.score()).isEqualTo(0.4);
    }

    @Test
    void decide_entryChecks_shiftWindowPermit() {
        // given
        ActiveShift outside = new ActiveShift("s1", Instant.EPOCH, Instant.EPOCH, false);

        // when / then
        assertThat(engine.decide(ctx(Direction.IN, profile("ACTIVE", TODAY), FACE_OK, null, null)).code()).isEqualTo(ReasonCode.NO_SHIFT);
        assertThat(engine.decide(ctx(Direction.IN, profile("ACTIVE", TODAY), FACE_OK, outside, null)).code())
                .isEqualTo(ReasonCode.OUTSIDE_SHIFT_WINDOW);
        assertThat(engine.decide(ctx(Direction.IN, profile("ACTIVE", TODAY.minusDays(1)), FACE_OK, SHIFT_OK, null)).code())
                .isEqualTo(ReasonCode.PERMIT_EXPIRED);
        assertThat(engine.decide(ctx(Direction.IN, profile("ACTIVE", null), FACE_OK, SHIFT_OK, null)).code())
                .isEqualTo(ReasonCode.PERMIT_EXPIRED);
    }

    @Test
    void decide_scoreBelowConfiguredThreshold_faceMismatch() {
        // given
        DecisionEngine strict = new DecisionEngine(TestProps.tuning(0.95, true));

        // when
        DecisionResult result = strict.decide(ctx(Direction.IN, profile("ACTIVE", TODAY), FACE_OK, SHIFT_OK, null));

        // then
        assertThat(result.code()).isEqualTo(ReasonCode.FACE_MISMATCH);
    }

    @Test
    void decide_shiftNotRequired_entryWithoutShiftAllowed() {
        // given
        DecisionEngine lenient = new DecisionEngine(TestProps.tuning(0.0, false));

        // when
        DecisionResult result = lenient.decide(ctx(Direction.IN, profile("ACTIVE", TODAY), FACE_OK, null, null));

        // then
        assertThat(result.decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void decide_exitWithoutShiftOrPermit_allowed() {
        // when
        DecisionResult result = engine.decide(ctx(Direction.OUT, profile("ACTIVE", TODAY.minusDays(30)), FACE_OK, null, null));

        // then
        assertThat(result.decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void decide_zoneWithoutPermit_noZonePermit() {
        // given
        PassContext ctx = new PassContext(Direction.IN, "d1", "roof", profile("ACTIVE", TODAY), FACE_OK, SHIFT_OK, null, TODAY);

        // when / then
        assertThat(engine.decide(ctx).code()).isEqualTo(ReasonCode.NO_ZONE_PERMIT);
    }

    private static WorkerProfile profile(String status, LocalDate permitUntil) {
        return new WorkerProfile("w1", status, "Иван Петров", "Монтажник", "СтройМонтаж", Set.of("d1"), permitUntil, Set.of("site"));
    }

    private static PassContext ctx(Direction direction, WorkerProfile profile, FaceResult face, ActiveShift shift, String failed) {
        return new PassContext(direction, "d1", null, profile, face, shift, failed, TODAY);
    }
}
