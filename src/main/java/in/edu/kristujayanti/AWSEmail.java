package in.edu.kristujayanti;


import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import software.amazon.awssdk.core.SdkBytes;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class AWSEmail {
    public void sendawsforgotpass(String token, String email) {
        SesV2Client client = SesV2Client.builder()
                .region(Region.AP_SOUTH_1)
                .build();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("otp", token);

        String templatePath = "src/main/java/in/edu/kristujayanti/emailtemplates/forgotpass.html";
        String logoPath = "src/main/java/in/edu/kristujayanti/emailtemplates/qvaultlogo.png";

        try {
            String htmlBody = Files.readString(Paths.get(templatePath));
            htmlBody = htmlBody.replace("{{logo}}", "cid:logoImage");

            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                htmlBody = htmlBody.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("qvaultkristujayanti@gmail.com"));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("Password Reset OTP");

            MimeMultipart multipart = new MimeMultipart("related");

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            MimeBodyPart imagePart = new MimeBodyPart();
            imagePart.attachFile(logoPath);
            imagePart.setContentID("<logoImage>");
            imagePart.setDisposition(MimeBodyPart.INLINE);
            multipart.addBodyPart(imagePart);

            message.setContent(multipart);
            message.writeTo(outputStream);

            RawMessage rawMessage = RawMessage.builder()
                    .data(SdkBytes.fromByteArray(outputStream.toByteArray()))
                    .build();

            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .fromEmailAddress("qvaultkristujayanti@gmail.com")
                    .content(EmailContent.builder().raw(rawMessage).build())
                    .build();

            client.sendEmail(emailRequest);
            System.out.println("✅ OTP email sent successfully to " + email);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public void sendawssignup(String token, String email) {
        SesV2Client client = SesV2Client.builder()
                .region(Region.AP_SOUTH_1)
                .build();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("otp", token);


        String templatePath = "src/main/java/in/edu/kristujayanti/emailtemplates/signupemail.html";
        String logoPath = "src/main/java/in/edu/kristujayanti/emailtemplates/qvaultlogo.png";

        try {
            String htmlBody = Files.readString(Paths.get(templatePath));


            htmlBody = htmlBody.replace("{{logo}}", "cid:logoImage");


            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                htmlBody = htmlBody.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("qvaultkristujayanti@gmail.com"));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("Your Sign-Up OTP");


            MimeMultipart multipart = new MimeMultipart("related");

            // HTML body
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);


            MimeBodyPart imagePart = new MimeBodyPart();
            imagePart.attachFile(logoPath);
            imagePart.setContentID("<logoImage>");
            imagePart.setDisposition(MimeBodyPart.INLINE);
            multipart.addBodyPart(imagePart);

            // Combine and write to output
            message.setContent(multipart);
            message.writeTo(outputStream);

            // Build SES raw message
            RawMessage rawMessage = RawMessage.builder()
                    .data(SdkBytes.fromByteArray(outputStream.toByteArray()))
                    .build();

            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .fromEmailAddress("qvaultkristujayanti@gmail.com")
                    .content(EmailContent.builder().raw(rawMessage).build())
                    .build();


            client.sendEmail(emailRequest);
            System.out.println("✅ Sign-up OTP email sent successfully to " + email);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
