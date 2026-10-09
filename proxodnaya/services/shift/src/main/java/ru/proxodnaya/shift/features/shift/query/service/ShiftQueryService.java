package ru.proxodnaya.shift.features.shift.query.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.shift.api.ActiveShift;
import ru.proxodnaya.shift.features.shift.api.ShiftLookup;
import ru.proxodnaya.shift.features.shift.model.Shift;
import ru.proxodnaya.shift.features.shift.model.ShiftFilter;
import ru.proxodnaya.shift.features.shift.model.ShiftTemplate;
import ru.proxodnaya.shift.features.shift.query.repository.ShiftReadRepository;
import ru.proxodnaya.shift.shared.Page;
import ru.proxodnaya.shift.shared.PageCursor;
import ru.proxodnaya.shift.shared.PageLimit;

/**
 * Чтение смен: списки, шаблоны и смена для решения о проходе (FR-07/21).
 */
@Service
@Transactional(readOnly = true)
public class ShiftQueryService implements ShiftLookup {

    private final ShiftReadRepository repository;
    private final ShiftProperties props;

    public ShiftQueryService(ShiftReadRepository repository, ShiftProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /**
     * Страница смен.
     *
     * @param filter фильтр
     * @param cursor курсор из {@code page.next} или {@code null}
     * @param limit  размер страницы или {@code null}
     * @return страница
     */
    public Page<Shift> page(ShiftFilter filter, String cursor, Integer limit) {
        int size = PageLimit.of(limit);
        List<Shift> rows = repository.page(filter, PageCursor.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        List<Shift> items = more ? rows.subList(0, size) : rows;
        String next = more ? new PageCursor(items.getLast().start(), items.getLast().id()).encode() : null;
        return new Page<>(List.copyOf(items), next, repository.count(filter));
    }

    /**
     * Все шаблоны.
     *
     * @return шаблоны по названию
     */
    public List<ShiftTemplate> templates() {
        return repository.templates();
    }

    @Override
    public Optional<ActiveShift> activeShift(String workerId, Instant moment) {
        Optional<Shift> covering = repository.covering(workerId, moment, props.earlyEntry());
        if (covering.isPresent()) {
            return covering.map(shift -> new ActiveShift(shift.id(), shift.start(), shift.end(), true));
        }
        ZoneId zone = props.siteZone();
        LocalDate day = moment.atZone(zone).toLocalDate();
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        return repository.nearestOnDay(workerId, dayStart, dayEnd, moment)
                .map(shift -> new ActiveShift(shift.id(), shift.start(), shift.end(), false));
    }
}
