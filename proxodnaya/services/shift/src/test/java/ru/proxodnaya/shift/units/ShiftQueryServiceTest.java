package ru.proxodnaya.shift.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.shift.api.ActiveShift;
import ru.proxodnaya.shift.features.shift.model.Shift;
import ru.proxodnaya.shift.features.shift.query.repository.ShiftReadRepository;
import ru.proxodnaya.shift.features.shift.query.service.ShiftQueryService;

class ShiftQueryServiceTest {

    private static final ShiftProperties PROPS = new ShiftProperties(ZoneId.of("Europe/Moscow"), Duration.ofMinutes(60),
            Duration.ofHours(2), Duration.ofHours(16));
    private static final Instant AT = Instant.parse("2026-10-09T04:30:00Z");
    private static final Shift DAY = new Shift(UUID.randomUUID(), "w1", Instant.parse("2026-10-09T05:00:00Z"),
            Instant.parse("2026-10-09T17:00:00Z"), null);

    private final ShiftReadRepository repository = mock(ShiftReadRepository.class);
    private final ShiftQueryService service = new ShiftQueryService(repository, PROPS);

    @Test
    void activeShift_inWindow_windowOk() {
        // given
        when(repository.covering("w1", AT, PROPS.earlyEntry())).thenReturn(Optional.of(DAY));

        // when
        Optional<ActiveShift> shift = service.activeShift("w1", AT);

        // then
        assertThat(shift).get().extracting(ActiveShift::windowOk).isEqualTo(true);
    }

    @Test
    void activeShift_sameDayOutsideWindow_notWindowOk() {
        // given: 03:00 МСК — день площадки 2026-10-09 начинается в 2026-10-08T21:00Z
        when(repository.covering(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.nearestOnDay(eq("w1"), eq(Instant.parse("2026-10-08T21:00:00Z")), eq(Instant.parse("2026-10-09T21:00:00Z")),
                any())).thenReturn(Optional.of(DAY));

        // when
        Optional<ActiveShift> shift = service.activeShift("w1", Instant.parse("2026-10-09T00:00:00Z"));

        // then
        assertThat(shift).get().extracting(ActiveShift::windowOk).isEqualTo(false);
    }

    @Test
    void activeShift_noShiftThatDay_empty() {
        // given
        when(repository.covering(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.nearestOnDay(any(), any(), any(), any())).thenReturn(Optional.empty());

        // when / then
        assertThat(service.activeShift("w1", AT)).isEmpty();
    }
}
