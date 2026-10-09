package ru.proxodnaya.access.api.mapper;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.api.dto.response.SettingsRs;
import ru.proxodnaya.access.features.settings.model.PassSettings;

/**
 * Настройки прохода → DTO (кабинет и терминал).
 */
@Component
public class SettingsApiMapper {

    /**
     * DTO настроек.
     *
     * @param value настройки
     * @return DTO
     */
    public SettingsRs convert(PassSettings value) {
        return new SettingsRs(value.faceThreshold(), value.qrToleranceSec(), value.shiftGraceMin(), value.requireShift(),
                value.repeatScanCooldownSec(), value.presenceTtlHours(), value.tempLockFailures(), value.tempLockWindowSec(),
                value.terminal().mode(), value.terminal().scope(), value.offline().policy(), value.offline().maxHours(),
                value.offline().afterExpiry(), value.offline().checkShift(), value.offline().unknownDevice(),
                value.updatedAt() == null ? null : value.updatedAt().toEpochMilli(), value.updatedBy());
    }
}
