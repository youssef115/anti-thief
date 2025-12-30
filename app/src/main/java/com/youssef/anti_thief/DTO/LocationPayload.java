package com.youssef.anti_thief.DTO;

public class LocationPayload {
    public double latitude;
    public double longitude;
    public String deviceId;

    public LocationPayload(double lat, double lng , String id){
        this.latitude=lat;
        this.longitude=lng;
        this.deviceId=id;
    }
}
