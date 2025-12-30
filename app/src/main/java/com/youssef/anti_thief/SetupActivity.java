package com.youssef.anti_thief;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.youssef.anti_thief.config.ConfigManager;
import com.youssef.anti_thief.utils.AESEncryption;


public class SetupActivity extends AppCompatActivity {

    private EditText serverUrlInput;
    private EditText senderEmailInput;
    private EditText emailPassInput;
    private EditText targetEmailInput;
    private EditText zipPasswordInput;
    private EditText aesKeyInput;
    private Button saveButton;
    private Button generateKeyButton;

    private ConfigManager configManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        configManager = new ConfigManager(this);


        serverUrlInput = findViewById(R.id.serverUrlInput);
        senderEmailInput = findViewById(R.id.senderEmailInput);
        emailPassInput = findViewById(R.id.emailPassInput);
        targetEmailInput = findViewById(R.id.targetEmailInput);
        zipPasswordInput = findViewById(R.id.zipPasswordInput);
        aesKeyInput = findViewById(R.id.aesKeyInput);
        saveButton = findViewById(R.id.saveButton);
        generateKeyButton = findViewById(R.id.generateKeyButton);


        loadExistingConfig();

        saveButton.setOnClickListener(v -> saveConfiguration());
        generateKeyButton.setOnClickListener(v -> generateAesKey());
    }

    private static final String DEFAULT_AES_KEY = "753dca4c445bbc2602f47c5337ae5067c1a767ead6d4dbc46d1e8102efd13ceb";

    private void loadExistingConfig() {
        if (configManager.isSetupComplete()) {
            serverUrlInput.setText(configManager.getServerUrl());
            senderEmailInput.setText(configManager.getEmailUser());
            emailPassInput.setText(configManager.getEmailPass());
            targetEmailInput.setText(configManager.getTargetEmail());
            zipPasswordInput.setText(configManager.getZipPassword());
            aesKeyInput.setText(configManager.getAesKey());
        } else {

            aesKeyInput.setText(DEFAULT_AES_KEY);
        }
    }

    private void generateAesKey() {
        String key = AESEncryption.generateKey();
        aesKeyInput.setText(key);
        Toast.makeText(this, "AES key generated! Save this key for your backend.", Toast.LENGTH_LONG).show();
    }

    private void saveConfiguration() {
        String serverUrl = serverUrlInput.getText().toString().trim();
        String senderEmail = senderEmailInput.getText().toString().trim();
        String emailPass = emailPassInput.getText().toString().trim();
        String targetEmail = targetEmailInput.getText().toString().trim();
        String zipPassword = zipPasswordInput.getText().toString().trim();
        String aesKey = aesKeyInput.getText().toString().trim();


        if (serverUrl.isEmpty()) {
            serverUrlInput.setError("Server URL is required");
            return;
        }
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrlInput.setError("URL must start with http:// or https://");
            return;
        }
        if (!serverUrl.endsWith("/")) {
            serverUrl = serverUrl + "/";
        }

        if (senderEmail.isEmpty()) {
            senderEmailInput.setError("Sender email is required");
            return;
        }
        if (!senderEmail.contains("@")) {
            senderEmailInput.setError("Invalid email format");
            return;
        }

        if (emailPass.isEmpty()) {
            emailPassInput.setError("App password is required");
            return;
        }

        if (targetEmail.isEmpty()) {
            targetEmailInput.setError("Target email is required");
            return;
        }
        if (!targetEmail.contains("@")) {
            targetEmailInput.setError("Invalid email format");
            return;
        }

        if (zipPassword.isEmpty()) {
            zipPasswordInput.setError("ZIP password is required");
            return;
        }
        if (zipPassword.length() < 4) {
            zipPasswordInput.setError("Password must be at least 4 characters");
            return;
        }

        if (aesKey.isEmpty()) {
            aesKeyInput.setError("AES key is required for secure communication");
            return;
        }
        if (aesKey.length() < 16) {
            aesKeyInput.setError("AES key must be at least 16 characters");
            return;
        }


        configManager.saveConfig(serverUrl, senderEmail, emailPass, targetEmail, zipPassword, aesKey);

        Toast.makeText(this, "Configuration saved!", Toast.LENGTH_SHORT).show();


        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
