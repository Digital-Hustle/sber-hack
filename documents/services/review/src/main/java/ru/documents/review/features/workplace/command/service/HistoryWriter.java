package ru.documents.review.features.workplace.command.service;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.documents.review.features.workplace.command.repository.HistoryRepository;
import ru.documents.review.features.workplace.persistence.RowMappers;

/**
 * Запись истории в той же транзакции, что и действие.
 */
@Component
public class HistoryWriter {

    /** Источник: оператор. */
    public static final String OPERATOR = "OPERATOR";
    /** Источник: модель (docproc). */
    public static final String MODEL = "MODEL";
    /** Источник: соискатель. */
    public static final String APPLICANT = "APPLICANT";

    private final HistoryRepository repo;
    private final RowMappers rows;
    private final Clock clock;

    public HistoryWriter(HistoryRepository repo, RowMappers rows, Clock clock) {
        this.repo = repo;
        this.rows = rows;
        this.clock = clock;
    }

    /**
     * Добавить запись.
     *
     * @param app     заявка
     * @param doc     документ или null
     * @param actor   кто
     * @param source  источник
     * @param action  действие
     * @param details детали
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(UUID app, UUID doc, String actor, String source, String action, Map<String, ?> details) {
        repo.add(UuidCreator.getTimeOrderedEpoch(), app, doc, actor, source, action, rows.write(details),
                clock.instant());
    }

    /**
     * Обезличить историю заявки (NFR-10).
     *
     * @param app заявка
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void redact(UUID app) {
        repo.redact(app);
    }
}
