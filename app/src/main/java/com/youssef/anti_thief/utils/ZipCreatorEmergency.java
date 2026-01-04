package com.youssef.anti_thief.utils;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import com.google.gson.GsonBuilder;
import com.youssef.anti_thief.config.Config;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * V4 - Emergency ZIP Creator
 * Creates encrypted ZIP with photos and location data
 * Falls back to cached locations if API fails (400/500 errors)
 */
public class ZipCreatorEmergency {

    private static final String TAG = "ZipCreatorEmergency";

    public static String createEmergencyZip(Context context, List<String> photoPaths, String alertType, Location currentLocation) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String zipFileName = "emergency_" + alertType.toLowerCase().replace(" ", "_") + "_" + timestamp + ".zip";
            File zipFile = new File(context.getExternalFilesDir(null), zipFileName);

            String password = Config.getZipPassword();
            if (password == null || password.isEmpty()) {
                Log.e(TAG, "ZIP password not configured");
                return null;
            }

            Log.d(TAG, "Creating emergency ZIP: " + zipFile.getAbsolutePath());

            ZipFile zip = new ZipFile(zipFile, password.toCharArray());

            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setEncryptFiles(true);
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
            zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

            List<String> addedPhotos = new ArrayList<>();

            // Add photos
            for (String photoPath : photoPaths) {
                File photoFile = new File(photoPath);
                if (photoFile.exists()) {
                    Log.d(TAG, "Adding photo: " + photoFile.getName());
                    zip.addFile(photoFile, zipParameters);
                    addedPhotos.add(photoFile.getName());
                }
            }

            // Try to get polyline from backend, fallback to cache if fails
            String encodedPolyline = fetchPolylineWithFallback(context);
            Log.d(TAG, "Location data source: " + (encodedPolyline.isEmpty() ? "NONE" : "OK"));

            // Create JSON data file
            File jsonFile = new File(context.getExternalFilesDir(null), "emergency_data.json");
            try (FileWriter writer = new FileWriter(jsonFile)) {
                String readableTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                
                writer.write("{\n");
                writer.write("  \"alertType\": \"" + alertType + "\",\n");
                writer.write("  \"timestamp\": \"" + readableTimestamp + "\",\n");
                writer.write("  \"deviceInfo\": \"" + Build.MANUFACTURER + " " + Build.MODEL + "\",\n");
                
                if (currentLocation != null) {
                    writer.write("  \"currentLocation\": {\n");
                    writer.write("    \"latitude\": " + currentLocation.getLatitude() + ",\n");
                    writer.write("    \"longitude\": " + currentLocation.getLongitude() + ",\n");
                    writer.write("    \"accuracy\": " + currentLocation.getAccuracy() + ",\n");
                    writer.write("    \"googleMapsUrl\": \"https://maps.google.com/?q=" + 
                            currentLocation.getLatitude() + "," + currentLocation.getLongitude() + "\"\n");
                    writer.write("  },\n");
                } else {
                    writer.write("  \"currentLocation\": null,\n");
                }
                
                writer.write("  \"photoFiles\": " + new GsonBuilder().create().toJson(addedPhotos) + ",\n");
                writer.write("  \"encodedPolyline\": \"" + encodedPolyline + "\"\n");
                writer.write("}");
            }
            zip.addFile(jsonFile, zipParameters);

            // Create HTML map
            File mapFile = new File(context.getExternalFilesDir(null), "location_map.html");
            try (FileWriter writer = new FileWriter(mapFile)) {
                writer.write(generateEmergencyMapHtml(alertType, encodedPolyline, currentLocation));
            }
            zip.addFile(mapFile, zipParameters);

            // Cleanup temp files
            jsonFile.delete();
            mapFile.delete();

            Log.d(TAG, "Emergency ZIP created: " + addedPhotos.size() + " photos");
            return zipFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Failed to create emergency ZIP", e);
            return null;
        }
    }

    /**
     * Fetches polyline from backend API
     * Falls back to cached locations if API returns 400/500 or fails
     */
    private static String fetchPolylineWithFallback(Context context) {
        // First try the backend API
        String polyline = fetchPolylineFromBackend();
        
        if (polyline != null && !polyline.isEmpty()) {
            Log.d(TAG, "Got polyline from backend API");
            return polyline;
        }

        // Fallback to cached locations
        Log.d(TAG, "Backend API failed, using cached locations");
        return generatePolylineFromCache(context);
    }

    private static String fetchPolylineFromBackend() {
        try {
            String serverUrl = Config.getServerUrl();
            String apiKey = Config.getApiKey();
            
            if (serverUrl == null || serverUrl.isEmpty()) {
                Log.e(TAG, "Server URL not configured");
                return null;
            }

            String url = serverUrl + "api/locations/24h";
            Log.d(TAG, "Fetching polyline from: " + url);

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS);

            OkHttpClient client = clientBuilder.build();

            Request.Builder requestBuilder = new Request.Builder().url(url).get();
            
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("X-API-Key", apiKey);
            }

            Response response = client.newCall(requestBuilder.build()).execute();

            if (response.isSuccessful() && response.body() != null) {
                String polyline = response.body().string().trim();
                // Remove quotes if present
                if (polyline.startsWith("\"") && polyline.endsWith("\"")) {
                    polyline = polyline.substring(1, polyline.length() - 1);
                }
                Log.d(TAG, "Backend returned polyline (length=" + polyline.length() + ")");
                return polyline;
            } else {
                Log.e(TAG, "Backend API failed with code: " + response.code());
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching from backend", e);
            return null;
        }
    }

    /**
     * Generates a polyline from cached locations when backend is unavailable
     */
    private static String generatePolylineFromCache(Context context) {
        try {
            LocationCache cache = new LocationCache(context);
            List<LocationCache.CachedLocation> locations = cache.getAllLocations();

            if (locations.isEmpty()) {
                Log.d(TAG, "No cached locations available");
                return "";
            }

            Log.d(TAG, "Generating polyline from " + locations.size() + " cached locations");
            return encodePolyline(locations);

        } catch (Exception e) {
            Log.e(TAG, "Error generating polyline from cache", e);
            return "";
        }
    }

    /**
     * Encodes a list of locations into a Google polyline format
     */
    private static String encodePolyline(List<LocationCache.CachedLocation> locations) {
        StringBuilder encoded = new StringBuilder();
        int prevLat = 0;
        int prevLng = 0;

        for (LocationCache.CachedLocation loc : locations) {
            int lat = (int) Math.round(loc.latitude * 1e5);
            int lng = (int) Math.round(loc.longitude * 1e5);

            encoded.append(encodeValue(lat - prevLat));
            encoded.append(encodeValue(lng - prevLng));

            prevLat = lat;
            prevLng = lng;
        }

        return encoded.toString();
    }

    private static String encodeValue(int value) {
        StringBuilder encoded = new StringBuilder();
        int v = value < 0 ? ~(value << 1) : (value << 1);
        
        while (v >= 0x20) {
            encoded.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        encoded.append((char) (v + 63));
        
        return encoded.toString();
    }

    private static String generateEmergencyMapHtml(String alertType, String encodedPolyline, Location currentLocation) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        
        String currentLat = currentLocation != null ? String.valueOf(currentLocation.getLatitude()) : "null";
        String currentLng = currentLocation != null ? String.valueOf(currentLocation.getLongitude()) : "null";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>EMERGENCY: " + alertType + "</title>\n" +
                "    <meta charset=\"utf-8\" />\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />\n" +
                "    <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n" +
                "    <style>\n" +
                "        body { margin: 0; padding: 0; font-family: Arial, sans-serif; }\n" +
                "        #map { height: 100vh; width: 100%; }\n" +
                "        .alert-box { position: absolute; top: 10px; right: 10px; z-index: 1000; background: #d32f2f; color: white; padding: 15px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.3); max-width: 320px; }\n" +
                "        .alert-box h2 { margin: 0 0 10px 0; font-size: 18px; }\n" +
                "        .alert-box p { margin: 5px 0; font-size: 14px; }\n" +
                "        .alert-box .timestamp { font-size: 12px; opacity: 0.9; }\n" +
                "        .legend { position: absolute; bottom: 30px; left: 10px; z-index: 1000; background: white; padding: 10px; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.2); }\n" +
                "        .legend-item { display: flex; align-items: center; margin: 5px 0; font-size: 13px; }\n" +
                "        .legend-current { width: 16px; height: 16px; background: #d32f2f; border-radius: 50%; margin-right: 8px; border: 2px solid white; box-shadow: 0 0 5px rgba(0,0,0,0.3); }\n" +
                "        .legend-path { width: 30px; height: 3px; background: #2196F3; margin-right: 8px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"map\"></div>\n" +
                "    <div class=\"alert-box\">\n" +
                "        <h2>EMERGENCY ALERT</h2>\n" +
                "        <p><strong>" + alertType + "</strong></p>\n" +
                "        <p class=\"timestamp\">" + timestamp + "</p>\n" +
                "        <hr style=\"border-color: rgba(255,255,255,0.3); margin: 10px 0;\">\n" +
                "        <p id=\"currentPos\">Current: Loading...</p>\n" +
                "        <p id=\"totalPoints\">History: Loading...</p>\n" +
                "    </div>\n" +
                "    <div class=\"legend\">\n" +
                "        <div class=\"legend-item\"><div class=\"legend-current\"></div> Current Position</div>\n" +
                "        <div class=\"legend-item\"><div class=\"legend-path\"></div> Movement History</div>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        var encodedPolyline = '" + encodedPolyline + "';\n" +
                "        var currentLat = " + currentLat + ";\n" +
                "        var currentLng = " + currentLng + ";\n" +
                "        \n" +
                "        function decodePolyline(encoded) {\n" +
                "            var points = [];\n" +
                "            var index = 0, len = encoded.length;\n" +
                "            var lat = 0, lng = 0;\n" +
                "            while (index < len) {\n" +
                "                var b, shift = 0, result = 0;\n" +
                "                do { b = encoded.charCodeAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);\n" +
                "                var dlat = ((result & 1) ? ~(result >> 1) : (result >> 1));\n" +
                "                lat += dlat;\n" +
                "                shift = 0; result = 0;\n" +
                "                do { b = encoded.charCodeAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);\n" +
                "                var dlng = ((result & 1) ? ~(result >> 1) : (result >> 1));\n" +
                "                lng += dlng;\n" +
                "                points.push([lat / 1e5, lng / 1e5]);\n" +
                "            }\n" +
                "            return points;\n" +
                "        }\n" +
                "        \n" +
                "        var historyPoints = decodePolyline(encodedPolyline);\n" +
                "        document.getElementById('totalPoints').innerHTML = 'History: ' + historyPoints.length + ' points';\n" +
                "        \n" +
                "        var centerLat = currentLat || (historyPoints.length > 0 ? historyPoints[historyPoints.length-1][0] : 35.6762);\n" +
                "        var centerLng = currentLng || (historyPoints.length > 0 ? historyPoints[historyPoints.length-1][1] : 10.0982);\n" +
                "        \n" +
                "        if (currentLat && currentLng) {\n" +
                "            document.getElementById('currentPos').innerHTML = 'Current: ' + currentLat.toFixed(6) + ', ' + currentLng.toFixed(6);\n" +
                "        } else {\n" +
                "            document.getElementById('currentPos').innerHTML = 'Current: Unknown';\n" +
                "        }\n" +
                "        \n" +
                "        var map = L.map('map').setView([centerLat, centerLng], 15);\n" +
                "        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: 'OpenStreetMap' }).addTo(map);\n" +
                "        \n" +
                "        if (historyPoints.length > 0) {\n" +
                "            var polyline = L.polyline(historyPoints, { color: '#2196F3', weight: 4, opacity: 0.7 }).addTo(map);\n" +
                "            map.fitBounds(polyline.getBounds(), { padding: [50, 50] });\n" +
                "        }\n" +
                "        \n" +
                "        if (currentLat && currentLng) {\n" +
                "            var currentMarker = L.marker([currentLat, currentLng], {\n" +
                "                icon: L.divIcon({\n" +
                "                    className: 'current-marker',\n" +
                "                    html: '<div style=\"background:#d32f2f;width:24px;height:24px;border-radius:50%;border:3px solid white;box-shadow:0 0 10px rgba(211,47,47,0.5);\"></div>',\n" +
                "                    iconSize: [30, 30],\n" +
                "                    iconAnchor: [15, 15]\n" +
                "                })\n" +
                "            }).addTo(map);\n" +
                "            currentMarker.bindPopup('<b>CURRENT POSITION</b><br>" + alertType + "<br><br>Lat: ' + currentLat.toFixed(6) + '<br>Lng: ' + currentLng.toFixed(6)).openPopup();\n" +
                "            map.setView([currentLat, currentLng], 16);\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
