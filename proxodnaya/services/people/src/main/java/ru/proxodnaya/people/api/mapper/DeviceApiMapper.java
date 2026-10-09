package ru.proxodnaya.people.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import ru.proxodnaya.people.api.dto.response.ActivationRs;
import ru.proxodnaya.people.api.dto.response.DeviceListRs;
import ru.proxodnaya.people.api.dto.response.DeviceRs;
import ru.proxodnaya.people.api.dto.response.InviteRs;
import ru.proxodnaya.people.features.device.model.Activation;
import ru.proxodnaya.people.features.device.model.Device;
import ru.proxodnaya.people.features.device.model.IssuedInvite;

/**
 * Модели устройств и инвайтов → DTO. Публичный ключ наружу не отдаём.
 */
@Component
public class DeviceApiMapper {

    /**
     * Инвайт.
     *
     * @param invite модель
     * @return DTO
     */
    public InviteRs toRs(IssuedInvite invite) {
        return new InviteRs(invite.code(), invite.link(), invite.link(), invite.expiresAt());
    }

    /**
     * Активация.
     *
     * @param activation модель
     * @return DTO
     */
    public ActivationRs toRs(Activation activation) {
        return new ActivationRs(activation.worker().id().toString(), activation.serverTime(),
                new ActivationRs.CardRs(activation.worker().fullName(), activation.worker().position(),
                        activation.worker().contractor()));
    }

    /**
     * Устройства.
     *
     * @param devices модели
     * @return DTO
     */
    public DeviceListRs toRs(List<Device> devices) {
        return new DeviceListRs(devices.stream()
                .map(device -> new DeviceRs(device.deviceId(), device.status(), device.boundAt(), device.revokedAt()))
                .toList());
    }
}
