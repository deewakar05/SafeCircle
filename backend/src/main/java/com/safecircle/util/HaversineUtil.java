package com.safecircle.util;

/**
 * Haversine Formula — calculates great-circle distance between two GPS points.
 * Returns distance in metres.
 */
public class HaversineUtil {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    public static double distanceInMetres(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }
}
