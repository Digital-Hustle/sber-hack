package ru.proxodnaya.shift.features.alert.command.service;

/**
 * Расстояние по поверхности Земли (гаверсинус) — для площадки в сотни метров точности хватает.
 */
public final class Geofence {

    private static final double EARTH_RADIUS_M = 6_371_000d;

    private Geofence() {
    }

    /**
     * Расстояние между точками, м.
     *
     * @param lat1 широта первой
     * @param lon1 долгота первой
     * @param lat2 широта второй
     * @param lon2 долгота второй
     * @return метры
     */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double hav = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(hav)));
    }
}
