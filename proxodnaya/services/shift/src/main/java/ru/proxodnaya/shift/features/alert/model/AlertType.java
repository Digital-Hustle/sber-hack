package ru.proxodnaya.shift.features.alert.model;

/**
 * Тип оповещения.
 */
public enum AlertType {
    /** B-01: человек в открытой смене вне геозоны. */
    LEFT_SITE(Severity.HIGH),
    /** B-01: телефон человека в открытой смене молчит дольше {@code NO_SIGNAL_AFTER}. */
    NO_SIGNAL(Severity.MEDIUM),
    /** B-05: в зоне больше {@code CROWD_LIMIT} человек дольше {@code CROWD_FOR}. */
    ZONE_CROWDED(Severity.HIGH);

    private final String severity;

    AlertType(Severity severity) {
        this(severity.name());
    }

    AlertType(String severity) {
        this.severity = severity;
    }

    /**
     * Важность.
     *
     * @return HIGH | MEDIUM
     */
    public String severity() {
        return severity;
    }

    /**
     * Важность оповещения.
     */
    private enum Severity {
        HIGH,
        MEDIUM
    }
}
