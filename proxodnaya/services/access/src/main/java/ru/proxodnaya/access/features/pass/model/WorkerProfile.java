package ru.proxodnaya.access.features.pass.model;

import java.time.LocalDate;
import java.util.Set;

/**
 * Профиль доступа рабочего из people ({@code /internal/people/workers/{id}/access-profile}).
 *
 * @param workerId    рабочий
 * @param status      ACTIVE | BLOCKED | DELETED
 * @param fullName    ФИО для экрана киоска
 * @param position    должность
 * @param contractor  подрядчик
 * @param deviceIds   привязанные устройства
 * @param permitUntil ближайший срок окончания допуска (инструктаж, медосмотр); пусто — допусков нет
 * @param zoneIds     зоны с допуском
 */
public record WorkerProfile(
        String workerId,
        String status,
        String fullName,
        String position,
        String contractor,
        Set<String> deviceIds,
        LocalDate permitUntil,
        Set<String> zoneIds
) {

    /**
     * Рабочий может проходить.
     *
     * @return статус ACTIVE
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
