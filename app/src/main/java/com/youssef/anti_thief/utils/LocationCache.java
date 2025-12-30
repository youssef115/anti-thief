package com.youssef.anti_thief.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LocationCache {

    private static final String PREFS_NAME = "location_cache";
    private static final String KEY_LOCATIONS = "cached_locations";
    
    private final SharedPreferences prefs;
    private final Gson gson;

    public LocationCache(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void addLocation(double latitude, double longitude, String deviceId) {
        List<CachedLocation> locations = getAllLocations();
        locations.add(new CachedLocation(latitude, longitude, deviceId, System.currentTimeMillis()));
        saveLocations(locations);
    }

    public List<CachedLocation> getAllLocations() {
        String json = prefs.getString(KEY_LOCATIONS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        
        Type type = new TypeToken<List<CachedLocation>>(){}.getType();
        List<CachedLocation> locations = gson.fromJson(json, type);
        return locations != null ? locations : new ArrayList<>();
    }

    public void clearOldLocations(int hoursToKeep) {
        List<CachedLocation> locations = getAllLocations();
        long cutoffTime = System.currentTimeMillis() - (hoursToKeep * 60 * 60 * 1000L);
        
        Iterator<CachedLocation> iterator = locations.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timestamp < cutoffTime) {
                iterator.remove();
            }
        }
        
        saveLocations(locations);
    }

    public void clearAll() {
        prefs.edit().remove(KEY_LOCATIONS).apply();
    }

    private void saveLocations(List<CachedLocation> locations) {
        String json = gson.toJson(locations);
        prefs.edit().putString(KEY_LOCATIONS, json).apply();
    }

    public static class CachedLocation {
        public double latitude;
        public double longitude;
        public String deviceId;
        public long timestamp;

        public CachedLocation(double latitude, double longitude, String deviceId, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.deviceId = deviceId;
            this.timestamp = timestamp;
        }
    }
}
