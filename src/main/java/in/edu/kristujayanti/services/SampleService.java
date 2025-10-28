package in.edu.kristujayanti.services;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.AWSEmail;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import io.vertx.core.AbstractVerticle;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;



public class SampleService extends AbstractVerticle {


    JwtUtil jtil = new JwtUtil();

    Jedis jedis = new Jedis("localhost", 6379);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    secretclass srt = new secretclass();
    AWSEmail ses= new AWSEmail();
    Vertx vertx = Vertx.vertx();
    HttpServer server = vertx.createHttpServer();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("QVault");
    MongoCollection<Document> usersdb = database.getCollection("Users");
    MongoCollection<Document> wishdb = database.getCollection("wishlist");
    MongoCollection<Document> pdfdb = database.getCollection("QuestionPapers");



    public void usersignup(RoutingContext ctx){
        JsonObject body=ctx.body().asJsonObject();
        String email=body.getString("email");
        String otp= body.getString("otp");
        String newpass= body.getString("password");
        String status="";

        Document docs = usersdb.find().filter(Filters.eq("email", email)).first();
        if (docs!=null){
            status="exist";
        }else {
            if (otp == null && newpass == null && email != null) {
                String send_otp = generateID(6);
                System.out.println(send_otp);
                setotp(send_otp,email);
                ses.sendawssignup(send_otp,email);
//                sendOTPmail(send_otp, email);
                status = "OTP Sent";
            } else if (email != null && otp != null && newpass != null) {
                String sent_otp = getoken(otp);
                if (sent_otp.equals(email)) {
                    if (email.matches(".*\\d.*") && email.contains("@kristujayanti.com")) {
                        String role = "Guest";
                        String designation="Student";
                        String hashpass = hashPassword(newpass);
                        System.out.println(hashpass);
                        Document insdoc = new Document("email", email).append("pass", hashpass).append("role", role).append("designation",designation);
                        InsertOneResult insres = usersdb.insertOne(insdoc);
                        if (insres.wasAcknowledged()) {
                            status = "success";
                            deltoken(otp);
                        }else{
                            deltoken(otp);
                        }
                    } else if (email.contains("@kristujayanti.com")) {
                        String role = "Guest";
                        String designation="Faculty";
                        String hashpass = hashPassword(newpass);
                        System.out.println(hashpass);
                        Document insdoc = new Document("email", email).append("pass", hashpass).append("role", role).append("designation",designation);
                        InsertOneResult insres = usersdb.insertOne(insdoc);
                        if (insres.wasAcknowledged()) {
                            status = "success";
                            deltoken(otp);
                        }else{
                            deltoken(otp);
                        }
                    }
                } else {
                    status = "otp invalid";
                    deltoken(otp);
                }

            } else {
                status = "invalid information";
            }
        }

        JsonObject job=new JsonObject().put("message",status);
        ctx.response().end(job.encode());
    }

    public void userlogin(RoutingContext ctx){
        JsonObject body=ctx.body().asJsonObject();
        String email=body.getString("email");
        String pass=body.getString("password");

        String acctoken="";
        String reftoken="";
        String status="";

        String token2=jedis.get("jwt:ref"+email);
        if (token2!=null){
            deltoken("jwt:acc"+email);
            deltoken("jwt:ref"+email);

        }

        Bson filt1=Filters.eq("email",email);
        Document matchdoc= usersdb.find(filt1).first();
        if(matchdoc==null){
            status="invalid username";
        }
        else{
            String dbpass=matchdoc.getString("pass");

            if(verifyPassword(pass,dbpass)){
                status="Login success";
                String dbrole=matchdoc.getString("role");
                acctoken= jtil.generateAccessToken(email,15,dbrole);
                reftoken=jtil.generateRefreshToken(email,7,dbrole);
                setoken("jwt:ref"+email,reftoken);
                System.out.println(getoken("jwt:ref"+email));
            }
            else{
                status="invalid passoword";
            }

        }
        JsonObject job=new JsonObject().put("message",status).put("access token",acctoken).put("refresh token",reftoken);
        ctx.response().end(job.encode());
    }

    public void resetpassword(RoutingContext ctx){
        JsonObject body=ctx.body().asJsonObject();
        String email=body.getString("email");
        String otp=body.getString("otp");
        String pass=body.getString("password");

        String status="";
        if (otp==null && pass==null){
            String send_otp = generateID(6);
            System.out.println(send_otp);
            setotp(send_otp,email);
            ses.sendawsforgotpass(send_otp,email);
//            sendresetmail(send_otp, email);
            status = "OTP Sent";

        }else if(email!=null && otp!=null & pass!=null)
        {
            String sent_otp = getoken(otp);
            if (sent_otp.equals(email)) {
                String hashpass = hashPassword(pass);
                Bson filter = Filters.eq("email", email);
                Bson update = Updates.set("pass", hashpass);
                UpdateResult res = usersdb.updateOne(filter, update);
                if (res.wasAcknowledged()) {
                    status = "success";
                    deltoken(sent_otp);
                }else{
                    status="update failed";
                }
            }
        }
        else{
            status="invalid information";
        }
        JsonObject job=new JsonObject().put("message",status);
        ctx.response().end(job.encode());

    }




    public static String generateID(int length) {
        String chars = "47689035635124012789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }

    public void handleupload(RoutingContext ctx) {
        Vertx vertx = Vertx.vertx();
        System.out.println("upload called");

        String name = ctx.request().getFormAttribute("course");
        String courseid = ctx.request().getFormAttribute("id");
        String department = ctx.request().getFormAttribute("department");
        String courseName = ctx.request().getFormAttribute("program");
        String examTerm = ctx.request().getFormAttribute("term");
        String year = ctx.request().getFormAttribute("year");
        JsonObject job=new JsonObject();

        try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");

            // Use GridFS to store PDFs
            GridFSBucket gridFSBucket = GridFSBuckets.create(database);
            List<ObjectId> pdfIds = new ArrayList<>();

            for (FileUpload upload : ctx.fileUploads()) {
                try {
                    if (upload.contentType().equals("application/pdf")) {
                        Path filePath = Paths.get(upload.uploadedFileName());
                        try (InputStream pdfStream = Files.newInputStream(filePath)) {
                            ObjectId fileId = gridFSBucket.uploadFromStream(upload.fileName(), pdfStream);
                            pdfIds.add(fileId);
                        }
                    } else {
                        System.out.println("Skipping non-PDF file: " + upload.fileName());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Store metadata and reference to GridFS files
            MongoCollection<Document> collection = database.getCollection("qpimage");

            Document doc = new Document("course", name)
                    .append("courseid", courseid)
                    .append("department", department)
                    .append("program", courseName)
                    .append("term", examTerm)
                    .append("year", year)
                    .append("fileIds", pdfIds);  // Save GridFS file references

            InsertOneResult ins= collection.insertOne(doc);
            if(ins.wasAcknowledged()){
                job.put("message","success");
            }else{
                job.put("message","fail");
            }
            ctx.response().end(job.encode());
            System.out.println("uploaded maybe");

        } catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(500).end("Failed to save PDFs with GridFS");
        }
    }



    public void sendOTPmail(String token,String email){
        String to = email;
        String from = srt.from;

        final String username = srt.username;
        final String password = srt.password;
        String host = "smtp.gmail.com";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");

        // create the Session object
        Session session = Session.getInstance(props,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            // create a MimeMessage object
            Message message = new MimeMessage(session);
            // set From email field
            message.setFrom(new InternetAddress(from));
            // set To email field
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            // set email subject field
            message.setSubject("Verify your account with this Code");
            // set the content of the email message
            String htmlContent =  "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "  <body style=\"font-family: Arial, sans-serif; padding: 20px; background-color: #ffffff;\">\n" +
                    "   \n" +
                    "    <!-- Logo -->\n" +
                    "    <div style=\"text-align: center; margin-bottom: 20px;\">\n" +
                    "      <img src=\"https://i.postimg.cc/QdKHj2Wp/Screenshot-2025-07-15-110351.png\n\" alt=\"Qvault Logo\" width=\"400\" height=\"225\"/>\n" +
                    "    </div>\n" +
                    "\n" +
                    "    <!-- Heading -->\n" +
                    "    <h2 style=\"color: #333; text-align: center; text-decoration: underline; text-underline-offset: 4px; font-size: 30px;\" >Verification Code</h2>\n" +
                    "\n" +
                    "    <!-- Message -->\n" +
                    "    <p style=\"font-size: 15px;\">Hi there,</p>\n" +
                    "    <p style=\"font-size: 15px;\">Please use the Verification Code below to verify your Account:</p>\n" +
                    "\n" +
                    "    <!-- Token Box -->\n" +
                    "    <div style=\"\n" +
                    "      text-align: center;\n" +
                    "      font-size: 35px;\n" +
                    "      font-weight: bold;\n" +
                    "      background: #f4f4f4;\n" +
                    "      border-radius: 8px;\n" +
                    "      padding: 14px;\n" +
                    "      width: fit-content;\n" +
                    "      margin: 20px auto;\n" +
                    "      color: #0066cc;\n" +
                    "      font-family: 'Courier New', Courier, monospace;\n" +
                    "      letter-spacing: 2px;\n" +
                    "    \">\n" +
                    token +
                    "    </div>\n" +
                    "\n" +
                    "    <!-- Expiry -->\n" +
                    "    <p style=\"color: red; font-weight: bold;\">Token is only valid for 5 Minutes.</p>\n" +
                    "\n" +
                    "    <!-- Ignore note -->\n" +
                    "    <p style=\"font-size: 14px; color: #555;\">\n" +
                    "      If you did not request this Verification code, someone is trying to use your account to sign up on our website.\n" +
                    "    </p>\n" +
                    "\n" +
                    "    <!-- Footer -->\n" +
                    "    <hr style=\"margin-top: 40px; border: none; border-top: 1px solid #ccc;\" />\n" +
                    "    <p style=\"font-size: 13px; color: #888;\">\n" +
                    "      Regards,<br />\n" +
                    "      <strong>The Qvault Team</strong><br />\n" +
                    "      © 2025 Qvault Inc. All rights reserved.\n" +
                    "    </p>\n" +
                    "  </body>\n" +
                    "</html>";

            message.setContent(htmlContent, "text/html");

            // send the email message
            Transport.send(message);

            System.out.println("Email Message token Sent Successfully!");

        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }

    }


    public void sendresetmail(String token,String email){
        String to = email;
        String from = srt.from;

        final String username = srt.username;
        final String password = srt.password;
        String host = "smtp.gmail.com";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");

        // create the Session object
        Session session = Session.getInstance(props,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            // create a MimeMessage object
            Message message = new MimeMessage(session);
            // set From email field
            message.setFrom(new InternetAddress(from));
            // set To email field
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            // set email subject field
            message.setSubject("Reset your QVault password");
            // set the content of the email message
            String htmlContent =  "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "  <body style=\"font-family: Arial, sans-serif; padding: 20px; background-color: #ffffff;\">\n" +
                    "   \n" +
                    "    <!-- Logo -->\n" +
                    "    <div style=\"text-align: center; margin-bottom: 20px;\">\n" +
                    "      <img src=\"https://i.postimg.cc/QdKHj2Wp/Screenshot-2025-07-15-110351.png\n\" alt=\"Qvault Logo\" width=\"400\" height=\"225\"/>\n" +
                    "    </div>\n" +
                    "\n" +
                    "    <!-- Heading -->\n" +
                    "    <h2 style=\"color: #333; text-align: center; text-decoration: underline; text-underline-offset: 4px; font-size: 27px;\" >Password Reset Code</h2>\n" +
                    "\n" +
                    "    <!-- Message -->\n" +
                    "    <p style=\"font-size: 15px;\">Hi there,</p>\n" +
                    "    <p style=\"font-size: 15px;\">Please use the Reset Code below to reset your password:</p>\n" +
                    "\n" +
                    "    <!-- Token Box -->\n" +
                    "    <div style=\"\n" +
                    "      text-align: center;\n" +
                    "      font-size: 35px;\n" +
                    "      font-weight: bold;\n" +
                    "      background: #f4f4f4;\n" +
                    "      border-radius: 8px;\n" +
                    "      padding: 14px;\n" +
                    "      width: fit-content;\n" +
                    "      margin: 20px auto;\n" +
                    "      color: #0066cc;\n" +
                    "      font-family: 'Courier New', Courier, monospace;\n" +
                    "      letter-spacing: 2px;\n" +
                    "    \">\n" +
                    token +
                    "    </div>\n" +
                    "\n" +
                    "    <!-- Expiry -->\n" +
                    "    <p style=\"color: red; font-weight: bold;\">Token is only valid for 5 Minutes.</p>\n" +
                    "\n" +
                    "    <!-- Ignore note -->\n" +
                    "    <p style=\"font-size: 14px; color: #555;\">\n" +
                    "      If you did not request this Reset Code, someone is trying access your account.\n" +
                    "    </p>\n" +
                    "\n" +
                    "    <!-- Footer -->\n" +
                    "    <hr style=\"margin-top: 40px; border: none; border-top: 1px solid #ccc;\" />\n" +
                    "    <p style=\"font-size: 13px; color: #888;\">\n" +
                    "      Regards,<br />\n" +
                    "      <strong>The Qvault Team</strong><br />\n" +
                    "      © 2025 Qvault Inc. All rights reserved.\n" +
                    "    </p>\n" +
                    "  </body>\n" +
                    "</html>";

            message.setContent(htmlContent, "text/html");

            // send the email message
            Transport.send(message);

            System.out.println("Email Message token Sent Successfully!");

        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }

    }





    public String hashPassword (String rawPassword){
        return passwordEncoder.encode(rawPassword);
    }
    public boolean verifyPassword (String rawPassword, String hashedPassword){
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    public void setoken(String key, String value)
    {
        jedis.set(key,value);
    }
    public void setotp(String key, String value)
    {
        jedis.setex(key,300,value);
    }
    public String getoken(String key){
        return jedis.get(key);
    }
    public void deltoken(String key){
        jedis.del(key);
    }

}
