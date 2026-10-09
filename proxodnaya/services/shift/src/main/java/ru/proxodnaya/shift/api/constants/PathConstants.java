package ru.proxodnaya.shift.api.constants;

/**
 * Пути API shift (gateway: {@code /shift/api/v1/...} → {@code /api/v1/...}).
 */
public final class PathConstants {

    public static final String API = "/api/v1";
    public static final String MY_SHIFTS = "/shifts";
    public static final String SHIFTS = API + MY_SHIFTS;
    public static final String BULK = "/bulk";
    public static final String BY_ID = "/{id}";
    public static final String TEMPLATES = API + "/shift-templates";
    public static final String INTERVALS = API + "/intervals";
    public static final String ANALYTICS = API + "/analytics";
    public static final String SUMMARY = "/summary";
    public static final String HOURS = "/hours";
    public static final String LATE_OVERTIME = "/late-overtime";
    public static final String WORKER_STREAM = "/worker";
    public static final String WORKER = API + WORKER_STREAM;
    public static final String ME = "/me";
    public static final String STREAM = API + "/stream";
    public static final String TICKET = "/ticket";
    public static final String SITUATION = "/situation";
    public static final String REPORTS = API + "/reports";
    public static final String TIMESHEET = "/timesheet.xlsx";
    public static final String INTERNAL_WORKERS = "/internal/shift/workers";
    public static final String ACTIVE_SHIFT = "/{id}/active-shift";

    private PathConstants() {
    }
}
