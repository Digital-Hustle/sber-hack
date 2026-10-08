package ru.proxodnaya.access.api.constants;

/**
 * Пути API access-сервиса.
 */
public final class PathConstants {

    public static final String API_V1 = "/api/v1";
    public static final String KIOSK = API_V1 + "/kiosk";
    public static final String KIOSK_ATTEMPTS = KIOSK + "/attempts";
    public static final String INTERNAL = API_V1 + "/internal";
    public static final String INTERNAL_STATS = INTERNAL + "/access/stats";
    public static final String ATTEMPTS = API_V1 + "/attempts";
    public static final String SECURITY = API_V1 + "/security";

    private PathConstants() {}
}
