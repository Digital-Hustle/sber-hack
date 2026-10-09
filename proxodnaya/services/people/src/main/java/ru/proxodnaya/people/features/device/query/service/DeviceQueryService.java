package ru.proxodnaya.people.features.device.query.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ru.proxodnaya.people.features.device.model.Device;
import ru.proxodnaya.people.features.device.query.repository.DeviceReadRepository;

/**
 * Устройства для кабинета.
 */
@Service
public class DeviceQueryService {

    private final DeviceReadRepository repository;

    public DeviceQueryService(DeviceReadRepository repository) {
        this.repository = repository;
    }

    /**
     * Устройства рабочего.
     *
     * @param workerId рабочий
     * @return устройства
     */
    public List<Device> byWorker(UUID workerId) {
        return repository.byWorker(workerId);
    }
}
