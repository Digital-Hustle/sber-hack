package ru.proxodnaya.access.api.constants;

/**
 * Пути API access (gateway добавляет префикс {@code /access}).
 */
public final class PathConstants {

    public static final String API_V1 = "/api/v1";
    public static final String KIOSK_ATTEMPTS = API_V1 + "/kiosk/attempts";
    public static final String FRAMES = "/{token}/frames";
    public static final String KIOSK_MANUAL = API_V1 + "/kiosk/manual";
    public static final String ATTEMPTS = API_V1 + "/attempts";
    public static final String SETTINGS = API_V1 + "/settings";
    public static final String WORKER_ATTEMPTS = API_V1 + "/worker/attempts";
    public static final String SECURITY = API_V1 + "/security";
    public static final String JOURNAL_VERIFY = "/journal/verify";
    public static final String SIMULATOR_RUNS = API_V1 + "/simulator/runs";

    private PathConstants() {
    }
}
