package com.swamyms.serverless;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
 import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
 import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;


public class UserVerificationLambda implements RequestHandler<SNSEvent, String> {

    private static final String SECRET_NAME = System.getenv("SECRET_NAME");
    private static final String REGION_NAME = System.getenv("REGION_NAME");

    @Override
    public String handleRequest(SNSEvent event, Context context) {
        context.getLogger().log("Start processing SNS event");

        // Fetch the credentials from Secrets Manager
        String secret = getSecret(SECRET_NAME);

        // Use Jackson ObjectMapper to parse the secret JSON
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode secretJson;
        try {
            secretJson = objectMapper.readTree(secret);
        } catch (Exception e) {
            context.getLogger().log("Error parsing secret: " + e.getMessage());
            return "Error parsing secret";
        }

        // Extract email service credentials from the secret
        String mailgunApiUrl = secretJson.get("MAILGUN_API_URL").asText();
        String mailgunApiKey = secretJson.get("MAILGUN_API_KEY").asText();
        String fromEmail = secretJson.get("FROM_EMAIL").asText();
        String verificationLink = secretJson.get("VERIFICATION_LINK").asText();

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
            sendVerificationEmail(userEmail,userFirstName,mailgunApiUrl,mailgunApiKey, fromEmail, verificationLink);
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

    public void sendVerificationEmail(String username, String userFirstName, String mailgunApiUrl,
                                      String mailgunApiKey, String fromEmail, String verificationLink) throws IOException {
        // Encode the username
        String encodedUsername = Base64.getUrlEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8));

        // Prepare the verification link (URL)
        String fullVerificationLink = verificationLink + encodedUsername;

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
                "      <a href=\"" + fullVerificationLink + "\" target=\"_blank\">Click here to verify your email</a>\n" +
                "    </p>\n" +
                "    <p>\n" +
                "      If you did not request this, please ignore this email.\n" +
                "    </p>\n" +
                "    <p>Best regards,</p>\n" +
                "    <p>Swamy Mudiga</p>\n" +
                "  </body>\n" +
                "</html>";

        // Prepare the request URL and body
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("api:" + mailgunApiKey).getBytes());

        // Prepare the request body
        String body = "from=" + URLEncoder.encode(fromEmail, StandardCharsets.UTF_8) +
                "&to=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                "&subject=" + URLEncoder.encode("Welcome to Swamy Mudiga Cloud Platform", StandardCharsets.UTF_8) +
                "&text=" + URLEncoder.encode("Hello, please verify your account by clicking the link below.", StandardCharsets.UTF_8) +
                "&html=" + URLEncoder.encode(message, StandardCharsets.UTF_8);


        // Create a URL object
        URL url = new URL(mailgunApiUrl);
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

    public String getSecret(String secretName) {

//        String secretName = "db-password";
        Region region = Region.of(REGION_NAME);

        // Create a Secrets Manager client
        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region)
                .build();

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse getSecretValueResponse;

        try {
            getSecretValueResponse = client.getSecretValue(getSecretValueRequest);

//             SecretsManager stores the secret as a string or binary. If it's stored as a string, return it.
            if (getSecretValueResponse.secretString() != null) {
                return getSecretValueResponse.secretString();
            } else {
                byte[] decodedBinarySecret = getSecretValueResponse.secretString().getBytes();
                return new String(decodedBinarySecret);
            }
        } catch (Exception e) {
            // For a list of exceptions thrown, see
            // https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html
            throw e;
        }
    }


}
