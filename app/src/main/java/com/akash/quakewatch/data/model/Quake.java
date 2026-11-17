package com.akash.quakewatch.data.model;

import java.util.Objects;

/**
 * Quake represents an earthquake event parsed from the USGS QuakeML feed.
 * It contains geographical coordinates, magnitude measurements, focal depth,
 * and the exact timestamp in epoch milliseconds.
 */
public class Quake {

    private final String id;
    private final String place;
    private final double magnitude;
    private final String magType;
    private final double depthKm;
    private final double latitude;
    private final double longitude;
    private final long timeMs;

    public Quake(String id, String place, double magnitude, String magType,
                 double depthKm, double latitude, double longitude, long timeMs) {
        this.id = id != null ? id : "";
        this.place = (place != null && !place.trim().isEmpty()) ? place.trim() : "Unknown location";
        this.magnitude = magnitude;
        this.magType = magType != null ? magType : "";
        this.depthKm = depthKm;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeMs = timeMs;
    }

    public String getId() {
        return id;
    }

    public String getPlace() {
        return place;
    }

    public double getMagnitude() {
        return magnitude;
    }

    public String getMagType() {
        return magType;
    }

    public double getDepthKm() {
        return depthKm;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getTimeMs() {
        return timeMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quake)) return false;
        Quake quake = (Quake) o;
        return Objects.equals(id, quake.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
