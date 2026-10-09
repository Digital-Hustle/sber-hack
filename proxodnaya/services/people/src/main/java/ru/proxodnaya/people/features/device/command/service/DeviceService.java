package ru.proxodnaya.people.features.device.command.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nimbusds.jose.jwk.ECKey;

import ru.proxodnaya.common.event.Topics;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.device.command.repository.DeviceRepository;
import ru.proxodnaya.people.features.device.command.repository.InviteRepository;
import ru.proxodnaya.people.features.device.model.Activation;
import ru.proxodnaya.people.features.worker.api.WorkerDirectory;
import ru.proxodnaya.people.features.worker.api.WorkerCard;
import ru.proxodnaya.people.messaging.outbox.Outbox;
import ru.proxodnaya.people.shared.WorkerStatus;

/**
 * Привязка и отвязка телефонов (PPL-04). События {@code people.device.v1} — в той же транзакции.
 */
@Service
public class DeviceService {

    private static final String DEVICE_ID = "deviceId";
    private static final String WORKER_ID = "workerId";

    private final InviteRepository invites;
    private final DeviceRepository devices;
    private final WorkerDirectory workers;
    private final InviteCodes codes;
    private final Outbox outbox;
    private final Clock clock;

    public DeviceService(InviteRepository invites, DeviceRepository devices, WorkerDirectory workers,
                         InviteCodes codes, Outbox outbox, Clock clock) {
        this.invites = invites;
        this.devices = devices;
        this.workers = workers;
        this.codes = codes;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Активация телефона по инвайту: гасит инвайт, отзывает прежний телефон, привязывает ключ.
     *
     * @param inviteCode код инвайта
     * @param deviceId   id устройства
     * @param jwk        публичный ключ (JWK)
     * @return рабочий и время сервера
     * @throws DomainException INVITE_INVALID, INVITE_EXPIRED, WORKER_BLOCKED, DEVICE_ALREADY_BOUND, VALIDATION_ERROR
     */
    @Transactional
    public Activation activate(String inviteCode, String deviceId, Map<String, Object> jwk) {
        final ECKey key = DeviceKeys.normalize(jwk);
        Instant now = clock.instant();
        InviteRepository.InviteRow invite = invites.lock(codes.hash(inviteCode))
                .filter(row -> !row.closed())
                .orElseThrow(() -> new DomainException(ErrorCode.INVITE_INVALID, "Код приглашения не найден или уже использован"));
        if (!now.isBefore(invite.expiresAt())) {
            throw new DomainException(ErrorCode.INVITE_EXPIRED, "Срок кода приглашения истёк — попросите новый");
        }
        WorkerCard worker = workers.lock(invite.workerId())
                .filter(found -> found.status() == WorkerStatus.ACTIVE)
                .orElseThrow(() -> new DomainException(ErrorCode.WORKER_BLOCKED, "Доступ заблокирован"));
        if (devices.exists(deviceId)) {
            throw new DomainException(ErrorCode.DEVICE_ALREADY_BOUND, "Это устройство уже привязано");
        }
        revoked(devices.revoke(worker.id(), null, now), worker.id());
        devices.bind(deviceId, worker.id(), key.toJSONString(), now);
        invites.markUsed(invite.id(), now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(DEVICE_ID, deviceId);
        payload.put(WORKER_ID, worker.id().toString());
        payload.put("publicKey", key.toJSONObject());
        outbox.add(Topics.PEOPLE_DEVICE_V1, "DeviceBound", deviceId, payload);
        return new Activation(worker, deviceId, now);
    }

    /**
     * Отвязка телефона администратором: access отказывает со следующего прохода.
     *
     * @param workerId рабочий
     * @param deviceId устройство
     * @throws DomainException NOT_FOUND, если у рабочего нет такого действующего устройства
     */
    @Transactional
    public void revoke(UUID workerId, String deviceId) {
        List<String> revoked = devices.revoke(workerId, deviceId, clock.instant());
        if (revoked.isEmpty()) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Действующее устройство не найдено");
        }
        revoked(revoked, workerId);
    }

    private void revoked(List<String> deviceIds, UUID workerId) {
        for (String deviceId : deviceIds) {
            outbox.add(Topics.PEOPLE_DEVICE_V1, "DeviceRevoked", deviceId,
                    Map.of(DEVICE_ID, deviceId, WORKER_ID, workerId.toString()));
        }
    }
}
