package ru.proxodnaya.access.api.mapper;

import java.time.Instant;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.api.dto.response.KioskCheckpointRs;
import ru.proxodnaya.access.api.dto.response.KioskRs;
import ru.proxodnaya.access.features.kiosk.model.CheckpointView;
import ru.proxodnaya.access.features.kiosk.model.Kiosk;

/**
 * Терминалы → DTO.
 */
@Component
public class KioskApiMapper {

    /**
     * Терминал. Код сопряжения — только у несопряжённого.
     *
     * @param kiosk терминал
     * @return DTO
     */
    public KioskRs convert(Kiosk kiosk) {
        return new KioskRs(kiosk.id().toString(), kiosk.name(), kiosk.paired() ? kiosk.checkpointId() : null, kiosk.mode(),
                kiosk.offlinePolicy(), kiosk.paired() ? null : kiosk.pairCode(), millis(kiosk.pairedAt()), millis(kiosk.lastSeen()),
                null, null, null);
    }

    /**
     * Проходная терминала.
     *
     * @param checkpoint проходная
     * @return DTO
     */
    public KioskCheckpointRs convert(CheckpointView checkpoint) {
        return new KioskCheckpointRs(checkpoint.id(), checkpoint.zoneId(), checkpoint.siteId(), checkpoint.name());
    }

    private static Long millis(Instant moment) {
        return moment == null ? null : moment.toEpochMilli();
    }
}
