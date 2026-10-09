package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ru.proxodnaya.access.features.pass.application.ManualPassService;
import ru.proxodnaya.access.features.pass.application.PassRecorder;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.model.PassOutcome;
import ru.proxodnaya.access.features.pass.model.PassRecord;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;
import ru.proxodnaya.access.features.pass.query.service.PassQueryService;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.access.support.TestProps;
import ru.proxodnaya.common.exception.NotFoundException;

class ManualPassServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T09:30:00Z");
    private static final WorkerProfile ACTIVE = new WorkerProfile("w1", "ACTIVE", "Иван Петров", "Монтажник", "СтройМонтаж",
            Set.of("d1"), LocalDate.of(2027, 1, 1), Set.of("z1"));

    private final PeopleGateway people = mock(PeopleGateway.class);
    private final PassQueryService query = mock(PassQueryService.class);
    private final PassRecorder recorder = mock(PassRecorder.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ManualPassService service = new ManualPassService(people, query, recorder, TestProps.defaults(),
            Clock.fixed(NOW, ZoneOffset.UTC), executor);

    @BeforeEach
    void setUp() {
        when(query.direction("w1", NOW)).thenReturn(Direction.IN);
        when(people.checkpointZone("cp1")).thenReturn(Optional.of("z1"));
        when(recorder.manual(any())).thenAnswer(call -> call.getArgument(0));
        when(recorder.deny(any())).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void close() {
        executor.close();
    }

    @Test
    void pass_activeWorker_manualGuardWithNoteAndGuardInJournal() {
        // given
        when(people.accessProfile("w1")).thenReturn(Optional.of(ACTIVE));

        // when
        PassOutcome outcome = service.pass("w1", "cp1", "  QR не читается, сверил паспорт ", "guard-7");

        // then
        assertThat(outcome.decision()).isEqualTo(Decision.MANUAL);
        assertThat(outcome.code()).isEqualTo(ReasonCode.MANUAL_GUARD);
        assertThat(outcome.worker().fullName()).isEqualTo("Иван Петров");
        ArgumentCaptor<PassRecord> saved = ArgumentCaptor.forClass(PassRecord.class);
        verify(recorder).manual(saved.capture());
        assertThat(saved.getValue().source()).isEqualTo(AttemptSource.MANUAL);
        assertThat(saved.getValue().zoneId()).isEqualTo("z1");
        assertThat(saved.getValue().details()).containsEntry("note", "QR не читается, сверил паспорт").containsEntry("guardId", "guard-7");
    }

    @Test
    void pass_blockedWorker_deniedAndJournaled() {
        // given
        when(people.accessProfile("w1")).thenReturn(Optional.of(new WorkerProfile("w1", "BLOCKED", "Иван Петров", "Монтажник",
                "СтройМонтаж", Set.of("d1"), LocalDate.of(2027, 1, 1), Set.of("z1"))));

        // when
        PassOutcome outcome = service.pass("w1", "cp1", "забыл телефон", "guard-7");

        // then
        assertThat(outcome.decision()).isEqualTo(Decision.DENY);
        assertThat(outcome.code()).isEqualTo(ReasonCode.WORKER_BLOCKED);
        verify(recorder, never()).manual(any());
    }

    @Test
    void pass_unknownCheckpointOrWorker_notFoundWithoutJournal() {
        // given
        when(people.accessProfile("w1")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.pass("w1", "cp1", "забыл телефон", "guard-7")).isInstanceOf(NotFoundException.class);
        when(people.checkpointZone("cp1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.pass("w1", "cp1", "забыл телефон", "guard-7")).isInstanceOf(NotFoundException.class);
        verify(recorder, never()).deny(any());
        verify(recorder, never()).manual(any());
    }

    @Test
    void pass_peopleDown_systemErrorFailClosed() {
        // given
        when(people.accessProfile("w1")).thenThrow(new IllegalStateException("down"));

        // when
        PassOutcome outcome = service.pass("w1", "cp1", "забыл телефон", "guard-7");

        // then
        assertThat(outcome.code()).isEqualTo(ReasonCode.SYSTEM_ERROR);
        verify(recorder, never()).manual(any());
    }
}
