package ru.proxodnaya.shift.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.common.exception.ConflictException;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.NotFoundException;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.shift.command.repository.ShiftRepository;
import ru.proxodnaya.shift.features.shift.command.repository.ShiftTemplateRepository;
import ru.proxodnaya.shift.features.shift.command.service.ShiftCommandService;
import ru.proxodnaya.shift.features.shift.model.BulkResult;
import ru.proxodnaya.shift.features.shift.model.ShiftDraft;
import ru.proxodnaya.shift.features.shift.model.WeekdayWindow;

class ShiftCommandServiceTest {

    private static final Instant START = Instant.parse("2026-10-09T05:00:00Z");
    private static final ShiftProperties PROPS = new ShiftProperties(ZoneId.of("Europe/Moscow"), Duration.ofMinutes(60),
            Duration.ofHours(2), Duration.ofHours(16));

    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final ShiftTemplateRepository templates = mock(ShiftTemplateRepository.class);
    private final ShiftCommandService service = new ShiftCommandService(shifts, templates, PROPS,
            Clock.fixed(START, ZoneOffset.UTC));

    @Test
    void create_overlapping_conflictAndNothingSaved() {
        // given
        when(shifts.overlaps(eq("w1"), any(), any())).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.create(new ShiftDraft("w1", START, START.plusSeconds(3600), null), "admin"))
                .isInstanceOf(ConflictException.class);
        verify(shifts).lockWorker("w1");
        verify(shifts, never()).insert(any(), any(), any());
    }

    @Test
    void create_endBeforeStartOrLongerThanDay_validationError() {
        // when / then
        assertThatThrownBy(() -> service.create(new ShiftDraft("w1", START, START, null), "admin"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.create(new ShiftDraft("w1", START, START.plus(Duration.ofHours(25)), null), "admin"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void create_unknownTemplate_notFound() {
        // given
        UUID template = UUID.randomUUID();
        when(templates.exists(template)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.create(new ShiftDraft("w1", START, START.plusSeconds(3600), template), "admin"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void bulk_skipsOverlappingCreatesRest() {
        // given: 2 рабочих × 2 дня, у w2 первый день уже занят
        when(shifts.overlaps(any(), any(), any())).thenReturn(false);
        when(shifts.overlaps(eq("w2"), eq(START), any())).thenReturn(true);
        List<WeekdayWindow> days = List.of(new WeekdayWindow(4, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                new WeekdayWindow(5, LocalTime.of(8, 0), LocalTime.of(20, 0)));

        // when
        BulkResult result = service.bulk(List.of("w2", "w1", "w1"), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10), days,
                "admin");

        // then
        assertThat(result.created()).isEqualTo(3);
        assertThat(result.skipped()).isEqualTo(1);
        verify(shifts, times(3)).insert(any(), eq("admin"), any());
    }

    @Test
    void bulk_duplicateWeekdayOrTooLong_validationError() {
        // given
        WeekdayWindow monday = new WeekdayWindow(0, LocalTime.of(8, 0), LocalTime.of(20, 0));

        // when / then
        assertThatThrownBy(() -> service.bulk(List.of("w1"), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
                List.of(monday, monday), "admin")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.bulk(List.of("w1"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31),
                List.of(monday), "admin")).isInstanceOf(DomainException.class);
    }
}
