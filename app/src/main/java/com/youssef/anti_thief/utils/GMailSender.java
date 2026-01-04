package com.youssef.anti_thief.utils;

import android.util.Log;

import com.youssef.anti_thief.config.Config;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class GMailSender {

    private static final String TAG = "GMailSender";

    /**
     * V4: Send text-only email (no attachment)
     */
    public static void sendEmail(String subject, String body) {
        Log.d(TAG, "=== sendEmail (text only) called ===");
        Log.d(TAG, "Subject: " + subject);

        Thread emailThread = new Thread(() -> {
            try {
                String emailUser = Config.getEmailUser();
                String emailPass = Config.getEmailPass();
                String targetEmail = Config.getTargetEmail();

                Log.d(TAG, "Email thread started - text only");
                Log.d(TAG, "From: " + emailUser);
                Log.d(TAG, "To: " + targetEmail);

                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");
                props.put("mail.smtp.connectiontimeout", "15000");
                props.put("mail.smtp.timeout", "15000");
                props.put("mail.smtp.writetimeout", "15000");

                Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(emailUser, emailPass);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(emailUser));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(targetEmail));
                message.setSubject(subject);
                message.setText(body);

                Log.d(TAG, ">>> Sending text email...");
                Transport.send(message);
                Log.d(TAG, "=== TEXT EMAIL SENT SUCCESSFULLY! ===");

            } catch (Exception e) {
                Log.e(TAG, "=== FAILED TO SEND TEXT EMAIL ===");
                Log.e(TAG, "Error: " + e.getMessage());
                e.printStackTrace();
            }
        });

        emailThread.setName("TextEmailSender-" + System.currentTimeMillis());
        emailThread.start();
    }

    public static void sendEmailWithZip(String zipPath, String subject) {
        Log.d(TAG, "=== sendEmailWithZip called ===");
        Log.d(TAG, "ZIP path: " + zipPath);
        Log.d(TAG, "Subject: " + subject);

        File zipFile = new File(zipPath);
        if (!zipFile.exists()) {
            Log.e(TAG, "ERROR: ZIP file does not exist: " + zipPath);
            return;
        }
        Log.d(TAG, "ZIP file exists, size: " + zipFile.length() + " bytes");

        Thread emailThread = new Thread(() -> {
            try {
                String emailUser = Config.getEmailUser();
                String emailPass = Config.getEmailPass();
                String targetEmail = Config.getTargetEmail();

                Log.d(TAG, "Email thread started - preparing to send ZIP");
                Log.d(TAG, "From: " + emailUser);
                Log.d(TAG, "To: " + targetEmail);

                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");
                props.put("mail.smtp.connectiontimeout", "30000");
                props.put("mail.smtp.timeout", "30000");
                props.put("mail.smtp.writetimeout", "30000");

                Log.d(TAG, "Creating mail session...");
                Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(emailUser, emailPass);
                    }
                });

                Log.d(TAG, "Building message...");
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(emailUser));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(targetEmail));
                message.setSubject(subject);

                Multipart multipart = new MimeMultipart();

                MimeBodyPart textPart = new MimeBodyPart();
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                String bodyText = "SECURITY ALERT\n\n" +
                        "Your device was accessed at: " + timestamp + "\n\n" +
                        "Attached is an encrypted ZIP file containing:\n" +
                        "- Photos captured during access (front + back cameras)\n" +
                        "- Location history from the last 24 hours\n" +
                        "- Interactive map showing device movement (location_map.html)\n\n" +
                        "This is an automated security alert from your Anti-Thief system.\n" +
                        "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
                textPart.setText(bodyText);
                multipart.addBodyPart(textPart);

                Log.d(TAG, "Adding ZIP attachment: " + zipFile.getName());
                MimeBodyPart attachmentPart = new MimeBodyPart();
                DataSource source = new FileDataSource(zipFile);
                attachmentPart.setDataHandler(new DataHandler(source));
                attachmentPart.setFileName(zipFile.getName());
                multipart.addBodyPart(attachmentPart);

                message.setContent(multipart);

                Log.d(TAG, ">>> Sending email with ZIP via Transport.send()...");
                Transport.send(message);
                Log.d(TAG, "=== EMAIL WITH ZIP SENT SUCCESSFULLY! ===");

                boolean deleted = zipFile.delete();
                Log.d(TAG, "ZIP file deleted after sending: " + deleted);

            } catch (Exception e) {
                Log.e(TAG, "=== FAILED TO SEND EMAIL WITH ZIP ===");
                Log.e(TAG, "Error type: " + e.getClass().getName());
                Log.e(TAG, "Error message: " + e.getMessage());
                e.printStackTrace();
            }
        });

        emailThread.setName("ZipEmailSender-" + System.currentTimeMillis());
        emailThread.start();
        Log.d(TAG, "ZIP email thread started: " + emailThread.getName());
    }

    public static void sendEmailWithAttachment(String imagePath, String subject) {
        Log.d(TAG, "=== sendEmailWithAttachment called ===");
        Log.d(TAG, "Image path: " + imagePath);
        Log.d(TAG, "Subject: " + subject);

        File checkFile = new File(imagePath);
        if (!checkFile.exists()) {
            Log.e(TAG, "ERROR: Image file does not exist: " + imagePath);
            return;
        }
        Log.d(TAG, "File exists, size: " + checkFile.length() + " bytes");

        Thread emailThread = new Thread(() -> {
            try {
                String emailUser = Config.getEmailUser();
                String emailPass = Config.getEmailPass();
                String targetEmail = Config.getTargetEmail();

                Log.d(TAG, "Email thread started - preparing to send");
                Log.d(TAG, "From: " + emailUser);
                Log.d(TAG, "To: " + targetEmail);

                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");
                props.put("mail.smtp.connectiontimeout", "15000");
                props.put("mail.smtp.timeout", "15000");
                props.put("mail.smtp.writetimeout", "15000");

                Log.d(TAG, "Creating mail session...");
                Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(emailUser, emailPass);
                    }
                });

                Log.d(TAG, "Building message...");
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(emailUser));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(targetEmail));
                message.setSubject(subject);

                Multipart multipart = new MimeMultipart();

                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText("Anti-Thief Alert!\n\nScreen was activated on your device.\nPhoto attached.\n\nTimestamp: " + new java.util.Date().toString());
                multipart.addBodyPart(textPart);

                File file = new File(imagePath);
                if (file.exists()) {
                    Log.d(TAG, "Adding attachment: " + file.getName());
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(file);
                    attachmentPart.setDataHandler(new DataHandler(source));
                    attachmentPart.setFileName(file.getName());
                    multipart.addBodyPart(attachmentPart);
                } else {
                    Log.e(TAG, "File no longer exists when attaching!");
                }

                message.setContent(multipart);

                Log.d(TAG, ">>> Sending email via Transport.send()...");
                Transport.send(message);
                Log.d(TAG, "=== EMAIL SENT SUCCESSFULLY! ===");

                if (file.exists()) {
                    boolean deleted = file.delete();
                    Log.d(TAG, "Photo deleted after sending: " + deleted);
                }

            } catch (Exception e) {
                Log.e(TAG, "=== FAILED TO SEND EMAIL ===");
                Log.e(TAG, "Error type: " + e.getClass().getName());
                Log.e(TAG, "Error message: " + e.getMessage());
                e.printStackTrace();
            }
        });

        emailThread.setName("EmailSender-" + System.currentTimeMillis());
        emailThread.start();
        Log.d(TAG, "Email thread started: " + emailThread.getName());
    }
}
