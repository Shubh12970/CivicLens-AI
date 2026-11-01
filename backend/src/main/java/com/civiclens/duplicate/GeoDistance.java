package com.civiclens.duplicate;

/** Great-circle distance (haversine) on a spherical Earth; accurate to well under 1% at city scale. */
public final class GeoDistance {

    public static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoDistance() {
    }

    public static double meters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Degrees of latitude spanning the given distance (for bounding-box prefilters). */
    public static double latDelta(double meters) {
        return Math.toDegrees(meters / EARTH_RADIUS_METERS);
    }

    /** Degrees of longitude spanning the given distance at a latitude (for bounding-box prefilters). */
    public static double lonDelta(double meters, double latitude) {
        double cos = Math.cos(Math.toRadians(latitude));
        return cos < 1e-6 ? 180.0 : Math.toDegrees(meters / (EARTH_RADIUS_METERS * cos));
    }
}
