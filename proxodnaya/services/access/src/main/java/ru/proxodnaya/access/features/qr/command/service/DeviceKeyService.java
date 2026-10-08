package ru.proxodnaya.access.features.qr.command.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.qr.api.DeviceKeyProjection;
import ru.proxodnaya.access.features.qr.command.repository.DeviceKeyRepository;

/**
 * Обновляет проекцию {@code device_keys}. Старое событие (по времени) не перетирает новое.
 */
@Service
public class DeviceKeyService implements DeviceKeyProjection {

    private final DeviceKeyRepository repository;
    private final PublicKeyCodec codec;

    public DeviceKeyService(DeviceKeyRepository repository, PublicKeyCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Override
    @Transactional
    public void bind(String deviceId, String workerId, String publicKey, Instant moment) {
        repository.upsert(deviceId, workerId, codec.toSpki(publicKey), moment);
    }

    @Override
    @Transactional
    public void revoke(String deviceId, Instant moment) {
        repository.revoke(deviceId, moment);
    }
}
