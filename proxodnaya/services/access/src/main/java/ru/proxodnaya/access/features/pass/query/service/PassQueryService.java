package ru.proxodnaya.access.features.pass.query.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.settings.api.PassTuning;
import ru.proxodnaya.access.features.pass.model.Presence;
import ru.proxodnaya.access.shared.Direction;
import ru.proxodnaya.access.features.pass.query.repository.PassReadRepository;

/**
 * Чтение для проверок до челленджа: статус, присутствие, повторный скан, блокировка за неудачи.
 */
@Service
@Transactional(readOnly = true)
public class PassQueryService {

    private final PassReadRepository repository;
    private final PassTuning tuning;

    public PassQueryService(PassReadRepository repository, PassTuning tuning) {
        this.repository = repository;
        this.tuning = tuning;
    }

    /**
     * Направление прохода по присутствию (ADR-037): внутри и вход моложе {@code presenceTtl} — выход, иначе вход.
     *
     * @param workerId рабочий
     * @param now      момент скана
     * @return IN или OUT
     */
    public Direction direction(String workerId, Instant now) {
        return presence(workerId).nextDirection(now, tuning.presenceTtl());
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
     * Временная блокировка после серии неудачных сверок лица (FR-16).
     *
     * @param workerId рабочий
     * @param now      текущий момент
     * @return true — заблокирован
     */
    public boolean tempLocked(String workerId, Instant now) {
        return faceFailuresSince(workerId, now.minus(tuning.tempLockWindow())) >= tuning.tempLockFailures();
    }

    /**
     * Был ли успешный проход в пределах паузы повторного скана.
     *
     * @param workerId рабочий
     * @param now      текущий момент
     * @return true — повторный скан
     */
    public boolean recentlyAllowed(String workerId, Instant now) {
        return lastAllowAt(workerId).filter(last -> last.isAfter(now.minus(tuning.repeatScanCooldown()))).isPresent();
    }
}
