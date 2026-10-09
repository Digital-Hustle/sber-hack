package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ru.proxodnaya.access.features.pass.application.DecisionEngine;
import ru.proxodnaya.access.features.pass.application.PassRecorder;
import ru.proxodnaya.access.features.pass.application.PassService;
import ru.proxodnaya.access.features.pass.command.service.ChallengeService;
import ru.proxodnaya.access.features.pass.gateway.FaceGateway;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.gateway.ShiftGateway;
import ru.proxodnaya.access.features.pass.model.ActiveShift;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.PassOutcome;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.pass.model.PendingAttempt;
import ru.proxodnaya.access.features.pass.model.ScanOutcome;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;
import ru.proxodnaya.access.features.pass.query.service.PassQueryService;
import ru.proxodnaya.access.features.qr.api.QrCheck;
import ru.proxodnaya.access.features.qr.api.QrCheckResult;
import ru.proxodnaya.access.shared.ChallengeAction;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.access.support.TestProps;
import ru.proxodnaya.common.exception.NotFoundException;

class PassServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T09:30:00Z");
    private static final WorkerProfile PROFILE = new WorkerProfile("w1", "ACTIVE", "Иван Петров", "Монтажник", "СтройМонтаж",
            Set.of("d1"), LocalDate.of(2027, 1, 1), Set.of("z1"));

    private final QrCheck qrCheck = mock(QrCheck.class);
    private final PassQueryService query = mock(PassQueryService.class);
    private final ChallengeService challenges = mock(ChallengeService.class);
    private final FaceGateway face = mock(FaceGateway.class);
    private final PeopleGateway people = mock(PeopleGateway.class);
    private final ShiftGateway shift = mock(ShiftGateway.class);
    private final PassRecorder recorder = mock(PassRecorder.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final PassService service = new PassService(qrCheck, query, challenges, face, people, shift, new DecisionEngine(),
            recorder, TestProps.defaults(), Clock.fixed(NOW, ZoneOffset.UTC), executor);

    @AfterEach
    void close() {
        executor.close();
    }

    @Test
    void scan_validQr_storesChallengeWithServerDirection() {
        // given
        validQr();
        when(query.direction("w1", NOW)).thenReturn(Direction.OUT);
        when(people.accessProfile("w1")).thenReturn(Optional.of(PROFILE));

        // when
        ScanOutcome outcome = service.scan("qr", "cp1");

        // then
        assertThat(outcome.isChallenge()).isTrue();
        assertThat(outcome.worker().fullName()).isEqualTo("Иван Петров");
        ArgumentCaptor<PendingAttempt> saved = ArgumentCaptor.forClass(PendingAttempt.class);
        verify(challenges).create(saved.capture());
        assertThat(saved.getValue().direction()).isEqualTo(Direction.OUT);
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(saved.getValue().zoneId()).isEqualTo("z1");
        verify(recorder, never()).deny(any());
    }

    @Test
    void scan_unknownCheckpoint_notFoundWithoutJournal() {
        // given
        validQr();
        when(people.checkpointZone("cp1")).thenReturn(Optional.empty());
        when(people.accessProfile("w1")).thenReturn(Optional.of(PROFILE));

        // when / then
        assertThatThrownBy(() -> service.scan("qr", "cp1")).isInstanceOf(NotFoundException.class);
        verify(recorder, never()).deny(any());
        verify(challenges, never()).create(any());
    }

    @Test
    void scan_badQr_journaledDenyWithoutChallenge() {
        // given
        when(qrCheck.check("bad", NOW)).thenReturn(QrCheckResult.fail(ReasonCode.QR_INVALID, null, null, 0));
        echoRecorder();

        // when
        ScanOutcome outcome = service.scan("bad", "cp1");

        // then
        assertThat(outcome.decision().code()).isEqualTo(ReasonCode.QR_INVALID);
        verify(challenges, never()).create(any());
    }

    @Test
    void scan_recentAllow_repeatScan() {
        // given
        validQr();
        when(query.direction("w1", NOW)).thenReturn(Direction.IN);
        when(query.lastAllowAt("w1")).thenReturn(Optional.of(NOW.minusSeconds(10)));
        echoRecorder();

        // when
        ScanOutcome outcome = service.scan("qr", "cp1");

        // then
        assertThat(outcome.decision().code()).isEqualTo(ReasonCode.REPEAT_SCAN);
        verify(people, never()).accessProfile(anyString());
    }

    @Test
    void scan_tooManyFaceFailures_tempLocked() {
        // given
        validQr();
        when(query.direction("w1", NOW)).thenReturn(Direction.IN);
        when(query.faceFailuresSince(eq("w1"), any())).thenReturn(5);
        echoRecorder();

        // when / then
        assertThat(service.scan("qr", "cp1").decision().code()).isEqualTo(ReasonCode.TEMP_LOCKED);
    }

    @Test
    void scan_peopleDown_systemErrorFailClosed() {
        // given
        validQr();
        when(query.direction("w1", NOW)).thenReturn(Direction.IN);
        when(people.accessProfile("w1")).thenThrow(new IllegalStateException("down"));
        echoRecorder();

        // when
        ScanOutcome outcome = service.scan("qr", "cp1");

        // then
        assertThat(outcome.decision().code()).isEqualTo(ReasonCode.SYSTEM_ERROR);
        verify(challenges, never()).create(any());
    }

    @Test
    void submitFrames_allOk_allowRecordedInOneTransaction() {
        // given
        pending(Direction.IN, NOW.plusSeconds(5));
        when(face.verify(eq("w1"), anyList(), any())).thenReturn(new FaceResult(true, 0.9, true, 0.8, null));
        when(people.accessProfile("w1")).thenReturn(Optional.of(PROFILE));
        when(shift.activeShift("w1", NOW)).thenReturn(Optional.of(new ActiveShift("s1", NOW, NOW, true)));
        when(recorder.finish(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        PassOutcome outcome = service.submitFrames(token(), List.of("a", "b", "c"));

        // then
        assertThat(outcome.decision()).isEqualTo(Decision.ALLOW);
        assertThat(outcome.worker().id()).isEqualTo("w1");
    }

    @Test
    void submitFrames_faceTimeout_systemErrorWithDependencyInDetails() {
        // given
        pending(Direction.OUT, NOW.plusSeconds(5));
        when(face.verify(eq("w1"), anyList(), any())).thenAnswer(inv -> {
            Thread.sleep(2_000);
            return new FaceResult(true, 0.9, true, 0.8, null);
        });
        when(people.accessProfile("w1")).thenReturn(Optional.of(PROFILE));
        ArgumentCaptor<PassRecord> rec = ArgumentCaptor.forClass(PassRecord.class);
        when(recorder.finish(rec.capture())).thenAnswer(inv -> inv.getArgument(0));

        // when
        PassOutcome outcome = service.submitFrames(token(), List.of("a", "b", "c"));

        // then
        assertThat(outcome.code()).isEqualTo(ReasonCode.SYSTEM_ERROR);
        assertThat(rec.getValue().details()).containsEntry("failedDependency", List.of("face"));
        verify(shift, never()).activeShift(anyString(), any());
    }

    @Test
    void submitFrames_expired_challengeExpiredWithoutConsumingQr() {
        // given
        pending(Direction.IN, NOW.minusSeconds(1));
        echoRecorder();

        // when
        PassOutcome outcome = service.submitFrames(token(), List.of("a", "b", "c"));

        // then
        assertThat(outcome.code()).isEqualTo(ReasonCode.CHALLENGE_EXPIRED);
        verify(recorder, never()).finish(any());
        verify(face, never()).verify(anyString(), anyList(), any());
    }

    @Test
    void submitFrames_unknownToken_notFound() {
        // given
        when(challenges.take(any())).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.submitFrames(UUID.randomUUID(), List.of("a"))).isInstanceOf(NotFoundException.class);
    }

    private void validQr() {
        when(qrCheck.check("qr", NOW)).thenReturn(QrCheckResult.ok("w1", "d1", 100L));
        when(people.checkpointZone("cp1")).thenReturn(Optional.of("z1"));
    }

    private void echoRecorder() {
        when(recorder.deny(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void pending(Direction direction, Instant expiresAt) {
        when(challenges.take(token())).thenReturn(Optional.of(new PendingAttempt(token(), "w1", "d1", 100L, direction, "cp1", "z1",
                ChallengeAction.BLINK, 3, NOW.minusSeconds(3), expiresAt)));
    }

    private static UUID token() {
        return UUID.fromString("00000000-0000-7000-8000-000000000001");
    }
}
