package com.swamyms.serverless;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Properties;

public class UserVerificationLambda implements RequestHandler<SNSEvent, String> {

    // Load environment variables
    private static final String SMTP_HOST = System.getenv("SMTP_HOST");
    private static final int SMTP_PORT = Integer.parseInt(System.getenv("SMTP_PORT"));
    private static final String SMTP_USERNAME = System.getenv("SMTP_USERNAME");
    private static final String SMTP_PASSWORD = System.getenv("SMTP_PASSWORD");
    private static final String SMTP_VERIFICATION_LINK = System.getenv("SMTP_VERIFICATION_LINK");
    private static final String SMTP_FROM_EMAIL = System.getenv("SMTP_FROM_EMAIL");

    private static final String DB_HOST_IP = System.getenv("DB_HOST_IP");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    private static final String DB_DATABASE = System.getenv("DB_DATABASE");
    private static final String DB_TABLE = System.getenv("DB_TABLE");

    @Override
    public String handleRequest(SNSEvent event, Context context) {
        event.getRecords().forEach(record -> {
            String userData = new String(Base64.getDecoder().decode(record.getSNS().getMessage()));
            String userEmail = parseUsernameFromJSON(userData); // You can adjust parsing if needed
            System.out.println("Received user email: " + userEmail);

            // Send email and update DB
            sendEmail(userEmail);
        });
        return "Execution completed";
    }

    private void sendEmail(String username) {
        try {
            String verificationLink = String.format("%s/%s", SMTP_VERIFICATION_LINK, username);
            String messageContent = String.format("""
                    <html>
                    <body>
                        <p>Dear %s,</p>
                        <p>Here is your verification link: <a href="%s" target="_blank">%s</a></p>
                    </body>
                    </html>
                    """, username, verificationLink, verificationLink);

            // Set up SMTP properties
            Properties properties = new Properties();
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.host", SMTP_HOST);
            properties.put("mail.smtp.port", String.valueOf(SMTP_PORT));

            // Get session
            Session session = Session.getInstance(properties, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
                }
            });

            // Prepare message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SMTP_FROM_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(username));
            message.setSubject("Verify Your Email");
            message.setContent(messageContent, "text/html");

            // Send message
            Transport.send(message);
            System.out.println("Email sent to user: " + username);

            // Store sent email record in DB
            storeSentEmail(username);

        } catch (Exception e) {
            System.err.println("Exception in sendEmail: " + e.getMessage());
        }
    }

    private void storeSentEmail(String username) {
        String dbUrl = "jdbc:postgresql://" + DB_HOST_IP + "/" + DB_DATABASE; // PostgreSQL JDBC URL
        String updateQuery = "UPDATE " + DB_TABLE + " SET email_sent = CURRENT_TIMESTAMP, verify_email_sent = TRUE WHERE username = ?";

        try (Connection connection = DriverManager.getConnection(dbUrl, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(updateQuery)) {

            statement.setString(1, username);
            int rowsAffected = statement.executeUpdate();
            System.out.println("Database update executed, rows affected: " + rowsAffected);

        } catch (Exception e) {
            System.err.println("Exception in storeSentEmail: " + e.getMessage());
        }
    }

    private String parseUsernameFromJSON(String userData) {
        // Assume JSON structure and parse username
        // Replace with your JSON parsing logic if necessary
        return userData.split(":")[1].replaceAll("\"", "").replaceAll("}", "").trim();
    }
}

