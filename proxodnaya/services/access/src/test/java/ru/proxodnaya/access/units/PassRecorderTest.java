package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.access.features.pass.application.PassRecorder;
import ru.proxodnaya.access.features.pass.command.service.JournalService;
import ru.proxodnaya.access.features.pass.command.service.PresenceService;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.qr.api.QrUses;
import ru.proxodnaya.access.messaging.outbox.Outbox;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.ChallengeAction;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.access.support.TestProps;

class PassRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-10T09:30:00Z");
    private static final Duration TTL = TestProps.defaults().presenceTtl();

    private final QrUses qrUses = mock(QrUses.class);
    private final JournalService journal = mock(JournalService.class);
    private final PresenceService presence = mock(PresenceService.class);
    private final Outbox outbox = mock(Outbox.class);
    private final PassRecorder recorder = new PassRecorder(qrUses, journal, presence, outbox, TestProps.defaults());

    @Test
    void finish_allowInWhilePresenceSaysInside_alreadyInsideWithoutPresenceChange() {
        // given
        when(qrUses.consume(anyString(), anyLong(), any(), any())).thenReturn(true);
        when(presence.lockDirection("w1", NOW, TTL)).thenReturn(Direction.OUT);

        // when
        PassRecord saved = recorder.finish(allow(Direction.IN));

        // then
        assertThat(saved.decision()).isEqualTo(Decision.DENY);
        assertThat(saved.code()).isEqualTo(ReasonCode.ALREADY_INSIDE);
        assertThat(saved.details()).containsEntry("computedCode", "OK");
        verify(journal).append(saved);
        verify(presence, never()).apply(any(), any(), any());
    }

    @Test
    void finish_allowOutAfterExitOnAnotherKiosk_notInside() {
        // given
        when(qrUses.consume(anyString(), anyLong(), any(), any())).thenReturn(true);
        when(presence.lockDirection("w1", NOW, TTL)).thenReturn(Direction.IN);

        // when
        PassRecord saved = recorder.finish(allow(Direction.OUT));

        // then
        assertThat(saved.code()).isEqualTo(ReasonCode.NOT_INSIDE);
        verify(presence, never()).apply(any(), any(), any());
    }

    @Test
    void finish_allowMatchingPresence_allowAndPresenceApplied() {
        // given
        when(qrUses.consume(anyString(), anyLong(), any(), any())).thenReturn(true);
        when(presence.lockDirection("w1", NOW, TTL)).thenReturn(Direction.IN);

        // when
        PassRecord saved = recorder.finish(allow(Direction.IN));

        // then
        assertThat(saved.decision()).isEqualTo(Decision.ALLOW);
        verify(presence).apply("w1", Direction.IN, NOW);
    }

    @Test
    void finish_qrAlreadyConsumed_qrReusedWithoutPassbackLock() {
        // given
        when(qrUses.consume(anyString(), anyLong(), any(), any())).thenReturn(false);

        // when
        PassRecord saved = recorder.finish(allow(Direction.IN));

        // then
        assertThat(saved.code()).isEqualTo(ReasonCode.QR_REUSED);
        verify(presence, never()).lockDirection(any(), any(), any());
    }

    @Test
    void manual_directionFromLockedPresence_noQrAndPresenceApplied() {
        // given
        when(presence.lockDirection("w1", NOW, TTL)).thenReturn(Direction.OUT);
        PassRecord manual = new PassRecord(UUID.randomUUID(), NOW, "cp1", "z1", Direction.IN, "w1", null, null, null, null, null,
                Decision.MANUAL, ReasonCode.MANUAL_GUARD, 3, AttemptSource.MANUAL, Map.of("note", "QR не читается"));

        // when
        PassRecord saved = recorder.manual(manual);

        // then
        assertThat(saved.direction()).isEqualTo(Direction.OUT);
        assertThat(saved.code()).isEqualTo(ReasonCode.MANUAL_GUARD);
        verify(qrUses, never()).consume(any(), anyLong(), any(), any());
        verify(presence).apply("w1", Direction.OUT, NOW);
        verify(outbox).add(any(), eq("PassDecided"), eq("w1"), any());
    }

    private static PassRecord allow(Direction direction) {
        return new PassRecord(UUID.randomUUID(), NOW, "cp1", "z1", direction, "w1", "d1", 1L, 0.9, true, ChallengeAction.values()[0],
                Decision.ALLOW, ReasonCode.OK, 5, AttemptSource.KIOSK, Map.of());
    }
}
