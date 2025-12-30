package com.youssef.anti_thief.utils;

import android.util.Log;

import com.youssef.anti_thief.config.Config;

import java.io.File;
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
                Log.d(TAG, "Email thread started - preparing to send");
                Log.d(TAG, "From: " + Config.EMAIL_USER);
                Log.d(TAG, "To: " + Config.TARGET_EMAIL);

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
                        return new PasswordAuthentication(Config.EMAIL_USER, Config.EMAIL_PASS);
                    }
                });

                Log.d(TAG, "Building message...");
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(Config.EMAIL_USER));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(Config.TARGET_EMAIL));
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
