package com.swamyms.serverless;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class UserVerificationLambda implements RequestHandler<SNSEvent, String> {

    private static final String MAILGUN_API_URL = System.getenv("MAILGUN_API_URL");
    private static final String MAILGUN_API_KEY = System.getenv("MAILGUN_API_KEY");
    private static final String FROM_EMAIL = System.getenv("FROM_EMAIL");
    private static final String VERIFICATION_LINK = System.getenv("VERIFICATION_LINK");

    @Override
    public String handleRequest(SNSEvent event, Context context) {
        context.getLogger().log("Start processing SNS event");

        // Network connectivity test
        try {
            InetAddress address = InetAddress.getByName("api.mailgun.net");
            context.getLogger().log("Mailgun IP: " + address.getHostAddress());
        } catch (UnknownHostException e) {
            context.getLogger().log("Failed to resolve api.mailgun.net: " + e.getMessage());
            return "Network issue: Unable to resolve Mailgun API endpoint";
        }

        String snsMessage = event.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log("Extracted SNS message: " + snsMessage);

        String userEmail = extractUserEmailFromMessage(snsMessage);
        String userFirstName = extractUserFirstNameFromMessage(snsMessage);
        context.getLogger().log("Extracted user email: " + userEmail);

        try {
            sendVerificationEmail(userEmail,userFirstName);
            context.getLogger().log("Email sent successfully to: " + userEmail);
        } catch (IOException e) {
            context.getLogger().log("Error sending email: " + e.getMessage());
            return "Error sending email";
        }

        context.getLogger().log("Processing complete");
        return "Email sent successfully";
    }



    private String extractUserEmailFromMessage(String snsMessage) {
        // Initialize Jackson ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Parse the JSON string
            JsonNode rootNode = objectMapper.readTree(snsMessage);

            // Extract the email from the parsed JSON
            JsonNode emailNode = rootNode.get("email");

            // Return the email if it exists
            if (emailNode != null) {
                return emailNode.asText();  // Convert the email node to a string
            } else {
                throw new IllegalArgumentException("Email field not found in the SNS message.");
            }
        } catch (Exception e) {
            // Handle exceptions (e.g., invalid JSON format)
            e.printStackTrace();
            return null;
        }
    }

    private String extractUserFirstNameFromMessage(String snsMessage) {
        // Initialize Jackson ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Parse the JSON string
            JsonNode rootNode = objectMapper.readTree(snsMessage);

            // Extract the email from the parsed JSON
            JsonNode firstNameNode = rootNode.get("firstName");

            // Return the email if it exists
            if (firstNameNode != null) {
                return firstNameNode.asText();  // Convert the email node to a string
            } else {
                throw new IllegalArgumentException("firstName field not found in the SNS message.");
            }
        } catch (Exception e) {
            // Handle exceptions (e.g., invalid JSON format)
            e.printStackTrace();
            return null;
        }
    }

    public void sendVerificationEmail(String username, String userFirstName) throws IOException {
        // Encode the username
        String encodedUsername = Base64.getUrlEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8));

        // Prepare the verification link (URL)
        String verificationLink = VERIFICATION_LINK + encodedUsername;

        // HTML message body with placeholders for dynamic content (using concatenation for multiline strings)
        String message = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "  <head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Verification Email</title>\n" +
                "    <style>\n" +
                "      body {\n" +
                "        font-family: Arial, sans-serif;\n" +
                "        color: #333;\n" +
                "        line-height: 1.6;\n" +
                "      }\n" +
                "      a {\n" +
                "        color: #007bff;\n" +
                "        text-decoration: none;\n" +
                "      }\n" +
                "      a:hover {\n" +
                "        text-decoration: underline;\n" +
                "      }\n" +
                "    </style>\n" +
                "  </head>\n" +
                "  <body>\n" +
                "    <p>Dear " + userFirstName + ",</p>\n" +
                "    <p>\n" +
                "      Thank you for creating user with our website. Please click the link below to verify your email address. Link expires within 2 minutes.\n" +
                "    </p>\n" +
                "    <p>\n" +
                "      <a href=\"" + verificationLink + "\" target=\"_blank\">Click here to verify your email</a>\n" +
                "    </p>\n" +
                "    <p>\n" +
                "      If you did not request this, please ignore this email.\n" +
                "    </p>\n" +
                "    <p>Best regards,</p>\n" +
                "    <p>Swamy Mudiga</p>\n" +
                "  </body>\n" +
                "</html>";

        // Prepare the request URL and body
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("api:" + MAILGUN_API_KEY).getBytes());

        // Prepare the request body
        String body = "from=" + URLEncoder.encode(FROM_EMAIL, StandardCharsets.UTF_8) +
                "&to=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                "&subject=" + URLEncoder.encode("Welcome to Swamy Mudiga Cloud Platform", StandardCharsets.UTF_8) +
                "&text=" + URLEncoder.encode("Hello, please verify your account by clicking the link below.", StandardCharsets.UTF_8) +
                "&html=" + URLEncoder.encode(message, StandardCharsets.UTF_8);


        // Create a URL object
        URL url = new URL(MAILGUN_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", authHeader);
        connection.setDoOutput(true);

        // Write the request body to the connection's output stream
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = body.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Get the response code and log it
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            System.out.println("Email sent successfully to: " + username);
        } else {
            System.out.println("Error: Unable to send email. Response Code: " + responseCode);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                System.out.println("Response Body: " + response.toString());
            }
        }
    }


}
