package ru.proxodnaya.shift.features.alert.query.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.exception.NotFoundException;
import ru.proxodnaya.shift.features.alert.model.Alert;
import ru.proxodnaya.shift.features.alert.query.repository.AlertReadRepository;

/**
 * Лента оповещений для админки.
 */
@Service
@Transactional(readOnly = true)
public class AlertQueryService {

    private final AlertReadRepository repository;

    public AlertQueryService(AlertReadRepository repository) {
        this.repository = repository;
    }

    /**
     * Последние оповещения.
     *
     * @param onlyOpen только не принятые
     * @param limit    1..200
     * @return оповещения, новые сверху
     */
    public List<Alert> latest(boolean onlyOpen, int limit) {
        return repository.latest(onlyOpen, limit);
    }

    /**
     * Оповещение.
     *
     * @param id id
     * @return оповещение
     * @throws NotFoundException если нет
     */
    public Alert get(UUID id) {
        return repository.byId(id).orElseThrow(() -> new NotFoundException("Оповещение не найдено"));
    }
}
