package com.youssef.anti_thief.utils;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.youssef.anti_thief.config.Config;
import com.youssef.anti_thief.DTO.LocationPayload;

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


public class ZipCreator {

    private static final String TAG = "ZipCreator";

    public static class SecurityPackage {
        public String timestamp;
        public List<String> photoFiles;
        public List<LocationPayload> locations;
        public String deviceInfo;

        public SecurityPackage() {
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            this.photoFiles = new ArrayList<>();
            this.locations = new ArrayList<>();
            this.deviceInfo = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        }
    }

    public static String createSecurityZip(Context context, List<String> photoPaths) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String zipFileName = "security_alert_" + timestamp + ".zip";
            File zipFile = new File(context.getExternalFilesDir(null), zipFileName);

            String password = Config.getZipPassword();
            if (password == null || password.isEmpty()) {
                Log.e(TAG, "ZIP password not configured");
                return null;
            }

            Log.d(TAG, "Creating encrypted ZIP: " + zipFile.getAbsolutePath());


            ZipFile zip = new ZipFile(zipFile, password.toCharArray());


            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setEncryptFiles(true);
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
            zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);


            SecurityPackage securityPackage = new SecurityPackage();


            for (String photoPath : photoPaths) {
                File photoFile = new File(photoPath);
                if (photoFile.exists()) {
                    Log.d(TAG, "Adding photo to ZIP: " + photoFile.getName());
                    zip.addFile(photoFile, zipParameters);
                    securityPackage.photoFiles.add(photoFile.getName());
                } else {
                    Log.w(TAG, "Photo file not found: " + photoPath);
                }
            }


            String encodedPolyline = fetchPolylineFromBackend();
            Log.d(TAG, "Fetched polyline: " + encodedPolyline);


            File jsonFile = new File(context.getExternalFilesDir(null), "security_data.json");
            try (FileWriter writer = new FileWriter(jsonFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();

                writer.write("{\n");
                writer.write("  \"timestamp\": \"" + securityPackage.timestamp + "\",\n");
                writer.write("  \"deviceInfo\": \"" + securityPackage.deviceInfo + "\",\n");
                writer.write("  \"photoFiles\": " + gson.toJson(securityPackage.photoFiles) + ",\n");
                writer.write("  \"encodedPolyline\": \"" + encodedPolyline + "\"\n");
                writer.write("}");
            }


            zip.addFile(jsonFile, zipParameters);


            File mapFile = new File(context.getExternalFilesDir(null), "location_map.html");
            try (FileWriter writer = new FileWriter(mapFile)) {
                writer.write(generateMapHtmlWithPolyline(encodedPolyline));
            }


            zip.addFile(mapFile, zipParameters);


            jsonFile.delete();
            mapFile.delete();

            Log.d(TAG, "ZIP created successfully: " + zipFile.getAbsolutePath());
            Log.d(TAG, "ZIP contains: " + securityPackage.photoFiles.size() + " photos, polyline data");

            return zipFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Failed to create security ZIP", e);
            return null;
        }
    }

    private static String fetchPolylineFromBackend() {
        try {
            String serverUrl = Config.getServerUrl();
            if (serverUrl == null || serverUrl.isEmpty()) {
                Log.e(TAG, "Server URL not configured");
                return "";
            }

            String url = serverUrl + "api/locations/24h";
            Log.d(TAG, "Fetching polyline from: " + url);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            Response response = client.newCall(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                String polyline = response.body().string().trim();
                // Remove quotes if present (JSON string response)
                if (polyline.startsWith("\"") && polyline.endsWith("\"")) {
                    polyline = polyline.substring(1, polyline.length() - 1);
                }
                Log.d(TAG, "Received polyline: " + polyline);
                return polyline;
            } else {
                Log.e(TAG, "Failed to fetch polyline: " + response.code());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching polyline from backend", e);
        }

        return "";
    }


    public static boolean deleteZipFile(String zipPath) {
        try {
            File zipFile = new File(zipPath);
            boolean deleted = zipFile.delete();
            Log.d(TAG, "ZIP file deleted: " + deleted + " - " + zipPath);
            return deleted;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete ZIP file: " + zipPath, e);
            return false;
        }
    }

    private static String generateMapHtmlWithPolyline(String encodedPolyline) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>🚨 Security Alert - Location History</title>\n" +
                "    <meta charset=\"utf-8\" />\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />\n" +
                "    <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n" +
                "    <style>\n" +
                "        body { margin: 0; padding: 0; font-family: Arial, sans-serif; }\n" +
                "        #map { height: 100vh; width: 100%; }\n" +
                "        .info-box {\n" +
                "            position: absolute;\n" +
                "            top: 10px;\n" +
                "            right: 10px;\n" +
                "            z-index: 1000;\n" +
                "            background: white;\n" +
                "            padding: 15px;\n" +
                "            border-radius: 8px;\n" +
                "            box-shadow: 0 2px 10px rgba(0,0,0,0.3);\n" +
                "            max-width: 300px;\n" +
                "        }\n" +
                "        .info-box h3 { margin: 0 0 10px 0; color: #d32f2f; }\n" +
                "        .info-box p { margin: 5px 0; font-size: 14px; }\n" +
                "        .legend {\n" +
                "            position: absolute;\n" +
                "            bottom: 30px;\n" +
                "            left: 10px;\n" +
                "            z-index: 1000;\n" +
                "            background: white;\n" +
                "            padding: 10px;\n" +
                "            border-radius: 5px;\n" +
                "            box-shadow: 0 2px 5px rgba(0,0,0,0.2);\n" +
                "        }\n" +
                "        .legend-item { display: flex; align-items: center; margin: 5px 0; }\n" +
                "        .legend-line { width: 30px; height: 3px; background: #2196F3; margin-right: 8px; }\n" +
                "        .legend-marker { width: 12px; height: 12px; background: #d32f2f; border-radius: 50%; margin-right: 8px; }\n" +
                "        .legend-start { width: 12px; height: 12px; background: #4CAF50; border-radius: 50%; margin-right: 8px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"map\"></div>\n" +
                "    <div class=\"info-box\" id=\"infoBox\">\n" +
                "        <h3>🚨 Security Alert</h3>\n" +
                "        <p><strong>Last Known Position:</strong></p>\n" +
                "        <p id=\"lastLat\">📍 Lat: Loading...</p>\n" +
                "        <p id=\"lastLng\">📍 Lng: Loading...</p>\n" +
                "        <p>🕐 Time: " + timestamp + "</p>\n" +
                "        <p id=\"totalPoints\"><strong>Total Points:</strong> Loading...</p>\n" +
                "    </div>\n" +
                "    <div class=\"legend\">\n" +
                "        <div class=\"legend-item\"><div class=\"legend-line\"></div> Movement Path</div>\n" +
                "        <div class=\"legend-item\"><div class=\"legend-marker\"></div> Last Position (Current)</div>\n" +
                "        <div class=\"legend-item\"><div class=\"legend-start\"></div> Start Point (24h ago)</div>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        // Encoded polyline from backend\n" +
                "        var encodedPolyline = '" + encodedPolyline + "';\n" +
                "        \n" +
                "        // Polyline decoding function (Google's algorithm)\n" +
                "        function decodePolyline(encoded) {\n" +
                "            var points = [];\n" +
                "            var index = 0, len = encoded.length;\n" +
                "            var lat = 0, lng = 0;\n" +
                "            \n" +
                "            while (index < len) {\n" +
                "                var b, shift = 0, result = 0;\n" +
                "                do {\n" +
                "                    b = encoded.charCodeAt(index++) - 63;\n" +
                "                    result |= (b & 0x1f) << shift;\n" +
                "                    shift += 5;\n" +
                "                } while (b >= 0x20);\n" +
                "                var dlat = ((result & 1) ? ~(result >> 1) : (result >> 1));\n" +
                "                lat += dlat;\n" +
                "                \n" +
                "                shift = 0;\n" +
                "                result = 0;\n" +
                "                do {\n" +
                "                    b = encoded.charCodeAt(index++) - 63;\n" +
                "                    result |= (b & 0x1f) << shift;\n" +
                "                    shift += 5;\n" +
                "                } while (b >= 0x20);\n" +
                "                var dlng = ((result & 1) ? ~(result >> 1) : (result >> 1));\n" +
                "                lng += dlng;\n" +
                "                \n" +
                "                points.push([lat / 1e5, lng / 1e5]);\n" +
                "            }\n" +
                "            return points;\n" +
                "        }\n" +
                "        \n" +
                "        // Decode the polyline\n" +
                "        var locations = decodePolyline(encodedPolyline);\n" +
                "        \n" +
                "        // Update info box\n" +
                "        document.getElementById('totalPoints').innerHTML = '<strong>Total Points:</strong> ' + locations.length;\n" +
                "        \n" +
                "        // Default center\n" +
                "        var centerLat = 35.6762;\n" +
                "        var centerLng = 10.0982;\n" +
                "        \n" +
                "        if (locations.length > 0) {\n" +
                "            var lastPos = locations[locations.length - 1];\n" +
                "            centerLat = lastPos[0];\n" +
                "            centerLng = lastPos[1];\n" +
                "            document.getElementById('lastLat').innerHTML = '📍 Lat: ' + centerLat.toFixed(6);\n" +
                "            document.getElementById('lastLng').innerHTML = '📍 Lng: ' + centerLng.toFixed(6);\n" +
                "        }\n" +
                "        \n" +
                "        // Initialize map\n" +
                "        var map = L.map('map').setView([centerLat, centerLng], 14);\n" +
                "        \n" +
                "        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {\n" +
                "            attribution: '© OpenStreetMap contributors'\n" +
                "        }).addTo(map);\n" +
                "        \n" +
                "        if (locations.length > 0) {\n" +
                "            // Draw polyline (movement path)\n" +
                "            var polyline = L.polyline(locations, {\n" +
                "                color: '#2196F3',\n" +
                "                weight: 4,\n" +
                "                opacity: 0.8\n" +
                "            }).addTo(map);\n" +
                "            \n" +
                "            // Fit map to show entire path\n" +
                "            map.fitBounds(polyline.getBounds(), { padding: [50, 50] });\n" +
                "            \n" +
                "            // Add marker for LAST position (thief's current location) - RED\n" +
                "            var lastPos = locations[locations.length - 1];\n" +
                "            var lastMarker = L.marker(lastPos, {\n" +
                "                icon: L.divIcon({\n" +
                "                    className: 'custom-marker',\n" +
                "                    html: '<div style=\\\"background:#d32f2f;width:20px;height:20px;border-radius:50%;border:3px solid white;box-shadow:0 2px 5px rgba(0,0,0,0.4);\\\"></div>',\n" +
                "                    iconSize: [26, 26],\n" +
                "                    iconAnchor: [13, 13]\n" +
                "                })\n" +
                "            }).addTo(map);\n" +
                "            \n" +
                "            lastMarker.bindPopup('<b>🚨 LAST KNOWN POSITION</b><br><br>This is where the device was <b>most recently</b> detected.<br><br>Lat: ' + lastPos[0].toFixed(6) + '<br>Lng: ' + lastPos[1].toFixed(6)).openPopup();\n" +
                "            \n" +
                "            // Add marker for START position (24h ago) - GREEN\n" +
                "            if (locations.length > 1) {\n" +
                "                var startPos = locations[0];\n" +
                "                L.marker(startPos, {\n" +
                "                    icon: L.divIcon({\n" +
                "                        className: 'start-marker',\n" +
                "                        html: '<div style=\\\"background:#4CAF50;width:14px;height:14px;border-radius:50%;border:2px solid white;box-shadow:0 2px 5px rgba(0,0,0,0.3);\\\"></div>',\n" +
                "                        iconSize: [18, 18],\n" +
                "                        iconAnchor: [9, 9]\n" +
                "                    })\n" +
                "                }).addTo(map).bindPopup('<b>Start Point</b><br>First recorded position (24h ago)<br><br>Lat: ' + startPos[0].toFixed(6) + '<br>Lng: ' + startPos[1].toFixed(6));\n" +
                "            }\n" +
                "        } else {\n" +
                "            document.getElementById('lastLat').innerHTML = '📍 Lat: No data';\n" +
                "            document.getElementById('lastLng').innerHTML = '📍 Lng: No data';\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";

        return html;
    }
}
