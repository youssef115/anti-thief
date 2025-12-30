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
    private static final String KEY_LAST_LOCATION_TIME = "last_location_time";
    private static final long MIN_INTERVAL_MS = 60000; // 1 minute minimum between locations
    
    private final SharedPreferences prefs;
    private final Gson gson;

    public LocationCache(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }


    public boolean addLocation(double latitude, double longitude, String deviceId) {
        long now = System.currentTimeMillis();
        long lastTime = prefs.getLong(KEY_LAST_LOCATION_TIME, 0);
        

        if (now - lastTime < MIN_INTERVAL_MS) {
            return false;
        }
        
        List<CachedLocation> locations = getAllLocations();
        locations.add(new CachedLocation(latitude, longitude, deviceId, now));
        saveLocations(locations);
        

        prefs.edit().putLong(KEY_LAST_LOCATION_TIME, now).apply();
        return true;
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

    public List<CachedLocation> getUnsyncedLocations() {
        return getAllLocations(); // For now, return all - we clear after successful sync
    }


    public void clearSyncedLocations() {
        prefs.edit().remove(KEY_LOCATIONS).apply();
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
        prefs.edit().remove(KEY_LOCATIONS).remove(KEY_LAST_LOCATION_TIME).apply();
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
