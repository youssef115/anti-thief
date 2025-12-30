package com.youssef.anti_thief.DTO;

public class LocationPayload {
    public double latitude;
    public double longitude;
    public String deviceId;
    public long timestamp;

    public LocationPayload(double lat, double lng, String id) {
        this.latitude = lat;
        this.longitude = lng;
        this.deviceId = id;
        this.timestamp = System.currentTimeMillis();
    }

    public LocationPayload(double lat, double lng, String id, long timestamp) {
        this.latitude = lat;
        this.longitude = lng;
        this.deviceId = id;
        this.timestamp = timestamp;
    }
}
