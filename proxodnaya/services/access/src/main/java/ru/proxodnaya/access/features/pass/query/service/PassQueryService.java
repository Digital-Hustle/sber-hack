package ru.proxodnaya.access.features.pass.query.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.config.properties.AccessProperties;
import ru.proxodnaya.access.features.pass.model.Presence;
import ru.proxodnaya.access.features.pass.query.model.AccessStats;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.features.pass.query.repository.PassReadRepository;
import ru.proxodnaya.access.jooq.tables.records.PassAttemptsRecord;

/**
 * Чтение для проверок до челленджа: статус, присутствие, повторный скан, блокировка за неудачи.
 */
@Service
@Transactional(readOnly = true)
public class PassQueryService {

    private final PassReadRepository repository;
    private final AccessProperties props;

    public PassQueryService(PassReadRepository repository, AccessProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /**
     * Направление прохода по присутствию (ADR-037): внутри и вход моложе {@code presenceTtl} — выход, иначе вход.
     *
     * @param workerId рабочий
     * @param now      момент скана
     * @return IN или OUT
     */
    public Direction direction(String workerId, Instant now) {
        Presence presence = presence(workerId);
        boolean inside = presence.inside() && presence.lastInAt() != null
                && presence.lastInAt().isAfter(now.minus(props.presenceTtl()));
        return inside ? Direction.OUT : Direction.IN;
    }

    /**
     * Рабочий заблокирован или удалён по проекции people.
     *
     * @param workerId рабочий
     * @return {@code true}, если статус известен и не ACTIVE
     */
    public boolean isBlocked(String workerId) {
        return repository.status(workerId).filter(status -> !"ACTIVE".equals(status)).isPresent();
    }

    /**
     * Присутствие.
     *
     * @param workerId рабочий
     * @return присутствие (нет данных — снаружи)
     */
    public Presence presence(String workerId) {
        return repository.presence(workerId).orElseGet(Presence::outside);
    }

    /**
     * Последний пропуск.
     *
     * @param workerId рабочий
     * @return время или пусто
     */
    public Optional<Instant> lastAllowAt(String workerId) {
        return repository.lastAllowAt(workerId);
    }

    /**
     * Неудачные сверки лица.
     *
     * @param workerId рабочий
     * @param since    начало окна
     * @return количество
     */
    public int faceFailuresSince(String workerId, Instant since) {
        return repository.faceFailuresSince(workerId, since);
    }

    /**
     * Журнал проходов по фильтру (FR-10/31).
     *
     * @param workerId   фильтр по рабочему (null — все)
     * @param checkpoint фильтр по проходной (null — все)
     * @param decision   фильтр по решению (null — все)
     * @param code       фильтр по коду причины (null — все)
     * @param source     фильтр по источнику (null — все)
     * @param from       от (включительно, null — без ограничения снизу)
     * @param to         до (включительно, null — без ограничения сверху)
     * @param cursor     курсор последней записи предыдущей страницы (null — первая страница)
     * @param limit      размер страницы
     * @return список записей журнала
     */
    public List<PassAttemptsRecord> findAttempts(
            String workerId, String checkpoint, String decision, String code, String source,
            Instant from, Instant to, String cursor, int limit) {
        Instant cursorTs = cursor != null && !cursor.isBlank() ? Instant.parse(cursor) : null;
        return repository.findAttempts(workerId, checkpoint, decision, code, source, from, to, cursorTs, limit);
    }

    /**
     * Количество попыток по фильтру.
     */
    public long countAttempts(
            String workerId, String checkpoint, String decision, String code, String source,
            Instant from, Instant to) {
        return repository.countAttempts(workerId, checkpoint, decision, code, source, from, to);
    }

    /**
     * Статистика для assistant (/internal/access/stats, FR-42).
     */
    public AccessStats stats(Instant from, Instant to) {
        return repository.stats(from, to);
    }
}
