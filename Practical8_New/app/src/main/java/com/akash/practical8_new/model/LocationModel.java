package com.akash.practical8_new.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Model representing a location fix and its synchronization metadata for Practical 8.
 */
public class LocationModel {
    private String id;
    private double latitude;
    private double longitude;
    private float accuracy;
    private String address;
    private long timestamp;
    private String deviceName;

    public LocationModel() {
        // Default constructor required for Firebase Firestore deserialization
    }

    public LocationModel(String id, double latitude, double longitude, float accuracy, String address, long timestamp, String deviceName) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.address = address != null ? address : "Unknown Location";
        this.timestamp = timestamp;
        this.deviceName = deviceName != null ? deviceName : "Android Device";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("accuracy", accuracy);
        map.put("address", address);
        map.put("timestamp", timestamp);
        map.put("deviceName", deviceName);
        return map;
    }
}
