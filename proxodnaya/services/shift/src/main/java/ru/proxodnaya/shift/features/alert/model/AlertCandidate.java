package ru.proxodnaya.shift.features.alert.model;

/**
 * Условие, найденное правилом на этом шаге.
 *
 * @param type     тип
 * @param workerId рабочий или {@code null}
 * @param zoneId   зона или {@code null}
 * @param details  что случилось
 */
public record AlertCandidate(AlertType type, String workerId, String zoneId, String details) {

    /**
     * Ключ: одно активное оповещение на тип и человека (или зону).
     *
     * @return ключ
     */
    public String key() {
        return type.name() + ":" + (workerId != null ? workerId : zoneId);
    }
}
