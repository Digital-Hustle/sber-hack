package ru.proxodnaya.common.event;

/**
 * Топики Kafka (см. docs/ARCHITECTURE.md «Схема событий»).
 * Топик: <владелец>.<агрегат>.v<N>, ключ — id агрегата, partitions 3.
 */
public final class Topics {

    public static final String ACCESS_PASS_V1 = "access.pass.v1";
    public static final String PEOPLE_WORKER_V1 = "people.worker.v1";
    public static final String PEOPLE_DEVICE_V1 = "people.device.v1";
    public static final String SHIFT_INTERVAL_V1 = "shift.interval.v1";
    public static final String SHIFT_ALERT_V1 = "shift.alert.v1";
    public static final String ASSISTANT_INSIGHT_V1 = "assistant.insight.v1";

    private Topics() {}
}
