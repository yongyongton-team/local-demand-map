package com.yongyongton.localdemand.common;

/** 좌표 계산 도우미. */
public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Geo() {
    }

    /** 두 좌표 사이 직선거리(km, 하버사인 공식). */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /** 소수 첫째 자리까지. 예: 1.84 → 1.8 */
    public static double round1(double km) {
        return Math.round(km * 10) / 10.0;
    }
}
