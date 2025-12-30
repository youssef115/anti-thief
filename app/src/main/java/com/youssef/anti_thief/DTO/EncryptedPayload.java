package com.youssef.anti_thief.DTO;

/**
 * Wrapper for encrypted API payloads.
 * All data sent to server is encrypted with AES-256-GCM.
 */
public class EncryptedPayload {
    
    private String data;
    private String deviceId;
    private long timestamp;

    public EncryptedPayload(String encryptedData, String deviceId) {
        this.data = encryptedData;
        this.deviceId = deviceId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
