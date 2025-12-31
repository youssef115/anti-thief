package com.youssef.anti_thief.config;

import android.content.Context;
import android.content.SharedPreferences;

public class ConfigManager {

    private static final String PREFS_NAME = "anti_thief_config";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_EMAIL_USER = "email_user";
    private static final String KEY_EMAIL_PASS = "email_pass";
    private static final String KEY_TARGET_EMAIL = "target_email";
    private static final String KEY_ZIP_PASSWORD = "zip_password";
    private static final String KEY_AES_KEY = "aes_key";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_SETUP_COMPLETE = "setup_complete";

    private final SharedPreferences prefs;

    public ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isSetupComplete() {
        return prefs.getBoolean(KEY_SETUP_COMPLETE, false);
    }

    public void saveConfig(String serverUrl, String emailUser, String emailPass,
                          String targetEmail, String zipPassword, String aesKey, String apiKey) {
        prefs.edit()
                .putString(KEY_SERVER_URL, serverUrl)
                .putString(KEY_EMAIL_USER, emailUser)
                .putString(KEY_EMAIL_PASS, emailPass)
                .putString(KEY_TARGET_EMAIL, targetEmail)
                .putString(KEY_ZIP_PASSWORD, zipPassword)
                .putString(KEY_AES_KEY, aesKey)
                .putString(KEY_API_KEY, apiKey)
                .putBoolean(KEY_SETUP_COMPLETE, true)
                .apply();
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public String getEmailUser() {
        return prefs.getString(KEY_EMAIL_USER, "");
    }

    public String getEmailPass() {
        return prefs.getString(KEY_EMAIL_PASS, "");
    }

    public String getTargetEmail() {
        return prefs.getString(KEY_TARGET_EMAIL, "");
    }

    public String getZipPassword() {
        return prefs.getString(KEY_ZIP_PASSWORD, "");
    }

    public String getAesKey() {
        return prefs.getString(KEY_AES_KEY, "");
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public void clearConfig() {
        prefs.edit().clear().apply();
    }
}
