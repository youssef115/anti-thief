package com.youssef.anti_thief.utils;

import android.util.Base64;
import android.util.Log;

import com.youssef.anti_thief.config.Config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class AESEncryption {

    private static final String TAG = "AESEncryption";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // bits


    public static String encrypt(String plaintext) {
        try {
            String key = Config.getAesKey();
            if (key == null || key.isEmpty()) {
                Log.e(TAG, "AES key not configured, returning plaintext");
                return plaintext;
            }


            SecretKeySpec secretKey = deriveKey(key);


            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);


            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);


            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));


            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            String result = Base64.encodeToString(combined, Base64.NO_WRAP);
            Log.d(TAG, "Encrypted " + plaintext.length() + " chars -> " + result.length() + " chars");
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }


    public static String decrypt(String encryptedBase64) {
        try {
            String key = Config.getAesKey();
            if (key == null || key.isEmpty()) {
                Log.e(TAG, "AES key not configured");
                return null;
            }


            SecretKeySpec secretKey = deriveKey(key);


            byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);


            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);


            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);


            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }


    private static SecretKeySpec deriveKey(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }


    public static String generateKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        StringBuilder sb = new StringBuilder();
        for (byte b : key) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
