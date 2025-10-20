package in.edu.kristujayanti;


import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;


import software.amazon.awssdk.services.sesv2.model.*;

import software.amazon.awssdk.services.sesv2.model.SesV2Exception;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class AWSEmail {
    public void sendawsforgotpass(String token, String email) {
        SesV2Client client = SesV2Client.builder()
                .region(Region.AP_SOUTH_1)
                .build();


        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("otp", token);

        // Path to your local template
        String templatePath = "src/main/java/in/edu/kristujayanti/emailtemplates/forgotpass.html";

        try {
            // Load HTML template
            String htmlBody = Files.readString(Paths.get(templatePath));

            // Replace placeholders with current values
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                htmlBody = htmlBody.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }

            // Build email
            Destination receiver = Destination.builder()
                    .toAddresses(email)
                    .build();

            Message msg = Message.builder()
                    .subject(Content.builder().data("Password Reset OTP").build())
                    .body(Body.builder()
                            .html(Content.builder().data(htmlBody).build())
                            .build())
                    .build();

            EmailContent emailContent = EmailContent.builder()
                    .simple(msg)
                    .build();

            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(receiver)
                    .content(emailContent)
                    .fromEmailAddress("qvaultkristujayanti@gmail.com")
                    .build();

            // Send email
            client.sendEmail(emailRequest);
            System.out.println("✅ OTP email sent successfully to " + email);

        } catch (IOException e) {
            System.err.println("Error reading template: " + e.getMessage());
        } catch (SesV2Exception e) {
            System.err.println("SES error: " + e.awsErrorDetails().errorMessage());


        }

    }

    public void sendawssignup(String token, String email){
        SesV2Client client = SesV2Client.builder()
                .region(Region.AP_SOUTH_1)
                .build();


        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("otp", token);

        // Path to your local template
        String templatePath = "src/main/java/in/edu/kristujayanti/emailtemplates/signupemail.html";

        try {
            // Load HTML template
            String htmlBody = Files.readString(Paths.get(templatePath));

            // Replace placeholders with current values
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                htmlBody = htmlBody.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }

            // Build email
            Destination receiver = Destination.builder()
                    .toAddresses(email)
                    .build();

            Message msg = Message.builder()
                    .subject(Content.builder().data("Sign Up OTP!!").build())
                    .body(Body.builder()
                            .html(Content.builder().data(htmlBody).build())
                            .build())
                    .build();

            EmailContent emailContent = EmailContent.builder()
                    .simple(msg)
                    .build();

            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(receiver)
                    .content(emailContent)
                    .fromEmailAddress("qvaultkristujayanti@gmail.com")
                    .build();

            // Send email
            client.sendEmail(emailRequest);
            System.out.println("✅ OTP email sent successfully to " + email);

        } catch (IOException e) {
            System.err.println("Error reading template: " + e.getMessage());
        } catch (SesV2Exception e) {
            System.err.println("SES error: " + e.awsErrorDetails().errorMessage());


        }



    }
}
