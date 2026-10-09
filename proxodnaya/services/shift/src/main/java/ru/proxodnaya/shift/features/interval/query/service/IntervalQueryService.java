package ru.proxodnaya.shift.features.interval.query.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.interval.api.Presence;
import ru.proxodnaya.shift.features.interval.api.PresenceLookup;
import ru.proxodnaya.shift.features.interval.model.Interval;
import ru.proxodnaya.shift.features.interval.model.IntervalFilter;
import ru.proxodnaya.shift.features.interval.query.repository.IntervalReadRepository;
import ru.proxodnaya.shift.shared.Page;
import ru.proxodnaya.shift.shared.PageCursor;
import ru.proxodnaya.shift.shared.PageLimit;

/**
 * Чтение интервалов присутствия.
 */
@Service
@Transactional(readOnly = true)
public class IntervalQueryService implements PresenceLookup {

    private final IntervalReadRepository repository;
    private final ShiftProperties props;

    public IntervalQueryService(IntervalReadRepository repository, ShiftProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /**
     * Страница интервалов.
     *
     * @param filter фильтр
     * @param cursor курсор или {@code null}
     * @param limit  размер страницы или {@code null}
     * @return страница
     */
    public Page<Interval> page(IntervalFilter filter, String cursor, Integer limit) {
        int size = PageLimit.of(limit);
        List<Interval> rows = repository.page(filter, PageCursor.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        List<Interval> items = more ? rows.subList(0, size) : rows;
        String next = more ? new PageCursor(items.getLast().enteredAt(), items.getLast().id()).encode() : null;
        return new Page<>(List.copyOf(items), next, repository.count(filter));
    }

    /**
     * Неполный ли интервал: конец не подтверждён выходом или открыт дольше {@code PRESENCE_TTL}.
     *
     * @param interval интервал
     * @param now      текущий момент
     * @return {@code true}, если цифрам по нему нельзя доверять полностью
     */
    public boolean incomplete(Interval interval, Instant now) {
        return interval.unconfirmed() || interval.exitedAt() == null && interval.enteredAt().isBefore(now.minus(props.presenceTtl()));
    }

    @Override
    public Optional<Presence> current(String workerId, Instant now) {
        return repository.openSince(workerId, now.minus(props.presenceTtl()))
                .map(interval -> new Presence(interval.zoneId(), interval.enteredAt()));
    }

    @Override
    public Duration worked(String workerId, Instant from, Instant until, Instant now) {
        return repository.worked(workerId, from, until, now, now.minus(props.presenceTtl()));
    }
}
