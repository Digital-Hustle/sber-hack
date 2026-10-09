package ru.proxodnaya.access.features.pass.query.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.pass.query.model.AttemptCursor;
import ru.proxodnaya.access.features.pass.query.model.AttemptFilter;
import ru.proxodnaya.access.features.pass.query.model.AttemptPage;
import ru.proxodnaya.access.features.pass.query.model.AttemptView;
import ru.proxodnaya.access.features.pass.query.repository.AttemptReadRepository;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Журнал проходов (FR-10/31): кабинет — с фильтрами, телефон — только свои проходы.
 */
@Service
@Transactional(readOnly = true)
public class AttemptQueryService {

    /** Размер страницы по умолчанию. */
    public static final int DEFAULT_LIMIT = 50;
    /** Максимальный размер страницы ({@code PAGE_MAX} в BACKEND-CONTRACT.md). */
    public static final int MAX_LIMIT = 200;

    private final AttemptReadRepository repository;

    public AttemptQueryService(AttemptReadRepository repository) {
        this.repository = repository;
    }

    /**
     * Страница журнала, новые сверху.
     *
     * @param filter фильтры
     * @param cursor курсор из {@code page.next} предыдущей страницы или {@code null}
     * @param limit  размер страницы (1..200, по умолчанию 50)
     * @return страница
     * @throws DomainException VALIDATION_ERROR: неверный курсор, limit или период
     */
    public AttemptPage page(AttemptFilter filter, String cursor, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "limit — от 1 до " + MAX_LIMIT);
        }
        if (filter.from() != null && filter.until() != null && !filter.from().isBefore(filter.until())) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Начало периода должно быть раньше конца");
        }
        List<AttemptView> rows = repository.page(filter, AttemptCursor.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        List<AttemptView> items = more ? rows.subList(0, size) : rows;
        String next = more ? new AttemptCursor(items.getLast().ts(), items.getLast().id()).encode() : null;
        return new AttemptPage(List.copyOf(items), next, repository.count(filter));
    }
}
