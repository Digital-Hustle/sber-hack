package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.access.features.pass.query.model.AttemptCursor;
import ru.proxodnaya.access.features.pass.query.model.AttemptFilter;
import ru.proxodnaya.access.features.pass.query.model.AttemptPage;
import ru.proxodnaya.access.features.pass.query.model.AttemptView;
import ru.proxodnaya.access.features.pass.query.repository.AttemptReadRepository;
import ru.proxodnaya.access.features.pass.query.service.AttemptQueryService;
import ru.proxodnaya.access.shared.AttemptSource;
import ru.proxodnaya.access.shared.Decision;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.common.exception.DomainException;

class AttemptQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T09:30:00Z");
    private static final AttemptFilter ALL = new AttemptFilter(null, null, null, null, null, null, null, null);

    private final AttemptReadRepository repository = mock(AttemptReadRepository.class);
    private final AttemptQueryService service = new AttemptQueryService(repository);

    @Test
    void page_moreRowsThanLimit_nextCursorPointsToLastShown() {
        // given
        List<AttemptView> rows = rows(3);
        when(repository.page(eq(ALL), eq(null), eq(3))).thenReturn(rows);
        when(repository.count(ALL)).thenReturn(10L);

        // when
        AttemptPage page = service.page(ALL, null, 2);

        // then
        assertThat(page.items()).hasSize(2);
        assertThat(page.total()).isEqualTo(10);
        AttemptCursor next = AttemptCursor.decode(page.next());
        assertThat(next.id()).isEqualTo(rows.get(1).id());
        assertThat(next.ts()).isEqualTo(rows.get(1).ts());
    }

    @Test
    void page_lastPage_noNextCursor() {
        // given
        when(repository.page(eq(ALL), eq(null), eq(51))).thenReturn(rows(2));

        // when / then
        assertThat(service.page(ALL, null, null).next()).isNull();
    }

    @Test
    void page_badLimitPeriodOrCursor_validationError() {
        // given
        AttemptFilter reversed = new AttemptFilter(null, null, null, null, null, null, NOW, NOW.minusSeconds(1));
        when(repository.page(any(), any(), eq(51))).thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> service.page(ALL, null, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.page(ALL, null, AttemptQueryService.MAX_LIMIT + 1)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.page(reversed, null, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.page(ALL, "не-курсор", null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AttemptCursor.decode("Zm9v")).isInstanceOf(DomainException.class);
    }

    private static List<AttemptView> rows(int count) {
        return IntStream.range(0, count)
                .mapToObj(idx -> new AttemptView(UUID.randomUUID(), NOW.minusSeconds(idx), "cp1", "z1", Direction.IN, "w1", Decision.ALLOW,
                        ReasonCode.OK, AttemptSource.KIOSK, 0.9, 120, null, null))
                .toList();
    }
}
