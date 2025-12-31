package com.youssef.anti_thief.config;

import android.content.Context;

public class Config {

    private static ConfigManager configManager;

    public static void init(Context context) {
        configManager = new ConfigManager(context.getApplicationContext());
    }

    public static String getServerUrl() {
        return configManager != null ? configManager.getServerUrl() : "";
    }

    public static String getEmailUser() {
        return configManager != null ? configManager.getEmailUser() : "";
    }

    public static String getEmailPass() {
        return configManager != null ? configManager.getEmailPass() : "";
    }

    public static String getTargetEmail() {
        return configManager != null ? configManager.getTargetEmail() : "";
    }

    public static String getZipPassword() {
        return configManager != null ? configManager.getZipPassword() : "";
    }

    public static String getAesKey() {
        return configManager != null ? configManager.getAesKey() : "";
    }

    public static String getApiKey() {
        return configManager != null ? configManager.getApiKey() : "";
    }

    public static boolean isSetupComplete() {
        return configManager != null && configManager.isSetupComplete();
    }
}
