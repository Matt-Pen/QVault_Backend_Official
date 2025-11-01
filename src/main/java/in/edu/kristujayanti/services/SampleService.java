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
        ctx.response().setChunked(true);
        String auth=ctx.request().getHeader("Authorization");

        System.out.println("upload called");

        if(auth==null || !auth.startsWith("Bearer ")){
            JsonObject job=new JsonObject().put("message","Invalid message");
            ctx.response().end(job.encode());
            System.out.println("auth null IF");
            return;
        }

        String token = auth.replace("Bearer ", "");
        if(jtil.isTokenValid(token))
        {
            System.out.println("token valid succes");
            String email= jtil.extractEmail(token);
            String role=jtil.extractRole(token);
            Bson filt1=Filters.eq("email",email);
            Document matchdoc= usersdb.find(filt1).first();

            if(matchdoc.getString("email")!=null && matchdoc.getString("role").equals(role) && role.equals("Admin"))
            {
                System.out.println("role and user valid valid succes");


                String name = ctx.request().getFormAttribute("course");
                String courseid = ctx.request().getFormAttribute("id");
                String department = ctx.request().getFormAttribute("department");
                String courseName = ctx.request().getFormAttribute("program");
                String examTerm = ctx.request().getFormAttribute("term");
                String year = ctx.request().getFormAttribute("year");
                String type=ctx.request().getFormAttribute("type");
                JsonObject job = new JsonObject();

                try {

                    GridFSBucket gridFSBucket = GridFSBuckets.create(database);

                    Document doc = new Document();


                    for (FileUpload upload : ctx.fileUploads()) {
                        try {
                            if (upload.contentType().equals("application/pdf")) {
                                Path filePath = Paths.get(upload.uploadedFileName());
                                try (InputStream pdfStream = Files.newInputStream(filePath)) {
                                    ObjectId fileId = gridFSBucket.uploadFromStream(upload.fileName(), pdfStream);
                                    doc.append("course", name)
                                            .append("courseid", courseid)
                                            .append("department", department)
                                            .append("program", courseName)
                                            .append("type",type)
                                            .append("term", examTerm)
                                            .append("year", year)
                                            .append("fileid", fileId);
                                }
                            } else {
                                System.out.println("Skipping non-PDF file: " + upload.fileName());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }


                    InsertOneResult ins = pdfdb.insertOne(doc);
                    if (ins.wasAcknowledged()) {
                        job.put("message", "success");
                    } else {
                        job.put("message", "fail");
                    }
                    ctx.response().end(job.encode());
                    System.out.println("uploaded maybe");

                } catch (Exception e) {
                    e.printStackTrace();
                    ctx.response().setStatusCode(500).end("Failed to save PDFs with GridFS");
                }

                for (FileUpload upload : ctx.fileUploads()) {
                    try {
                        Files.deleteIfExists(Paths.get(upload.uploadedFileName()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            else{
                JsonObject job=new JsonObject().put("message","Unauthorized Access");
                ctx.response().setStatusCode(401).end(job.encode());

            }
        }
        else {
            JsonObject job=new JsonObject().put("message","Expired or Invalid");
            ctx.response().setStatusCode(401).end(job.encode());

        }
    }


    public void handleupdate(RoutingContext ctx){
        ctx.response().setChunked(true);
        String auth=ctx.request().getHeader("Authorization");

        System.out.println("upload called");

        if(auth==null || !auth.startsWith("Bearer ")){
            JsonObject job=new JsonObject().put("message","Invalid message");
            ctx.response().end(job.encode());
            System.out.println("auth null IF");
            return;
        }

        String token = auth.replace("Bearer ", "");
        if(jtil.isTokenValid(token)) {
            System.out.println("token valid succes");
            String email = jtil.extractEmail(token);
            String role = jtil.extractRole(token);
            Bson filt1 = Filters.eq("email", email);
            Document matchdoc = usersdb.find(filt1).first();

            if (matchdoc.getString("email") != null && matchdoc.getString("role").equals(role) && role.equals("Admin")) {
                System.out.println("role and user valid valid succes");
                String contentType = ctx.request().getHeader("Content-Type");
                if(contentType!=null && contentType.contains("application/json")){
                    JsonObject body=ctx.body().asJsonObject();
                    ObjectId id=new ObjectId(body.getString("id"));
                    Bson filter = Filters.eq("_id",id);
                    Bson update = Updates.combine(
                            Updates.set("course", body.getString("course")),
                            Updates.set("courseid", body.getString("courseid")),
                            Updates.set("department", body.getString("department")),
                            Updates.set("program", body.getString("program")),
                            Updates.set("type", body.getString("type")),
                            Updates.set("term", body.getString("term")),
                            Updates.set("year", body.getString("year"))
                            );

                    UpdateResult result2 = pdfdb.updateOne(filter, update);
                    if(result2.getModifiedCount()!=0){
                        JsonObject job=new JsonObject().put("message","Updated successfully");
                        ctx.response().setStatusCode(200).end(job.encode());
                        return;
                    }
                    else{
                        JsonObject job=new JsonObject().put("message","Update Failed");
                        ctx.response().setStatusCode(400).end(job.encode());
                        return;
                    }


                } else if(contentType!=null && contentType.contains("multipart/form-data")){
                    ObjectId id=new ObjectId(ctx.request().getFormAttribute("id"));
                    ObjectId pdfid=new ObjectId(ctx.request().getFormAttribute("fileid"));


                    ObjectId newpdfid= null;
                    GridFSBucket gridFSBucket = GridFSBuckets.create(database);

                    for (FileUpload upload : ctx.fileUploads()) {
                        try {
                            if (upload.contentType().equals("application/pdf")) {
                                Path filePath = Paths.get(upload.uploadedFileName());
                                try (InputStream pdfStream = Files.newInputStream(filePath)) {
                                    newpdfid = gridFSBucket.uploadFromStream(upload.fileName(), pdfStream);

                                }
                            } else {
                                System.out.println("Skipping non-PDF file: " + upload.fileName());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (newpdfid == null) {
                        ctx.response().setStatusCode(400)
                                .end(new JsonObject().put("message", "No valid PDF uploaded").encode());
                        return;
                    }

                    gridFSBucket.delete(pdfid);

                    Bson filter = Filters.eq("_id",id);
                    Bson update = Updates.combine(
                            Updates.set("course", ctx.request().getFormAttribute("course")),
                            Updates.set("courseid", ctx.request().getFormAttribute("courseid")),
                            Updates.set("department", ctx.request().getFormAttribute("department")),
                            Updates.set("program", ctx.request().getFormAttribute("program")),
                            Updates.set("type", ctx.request().getFormAttribute("type")),
                            Updates.set("term", ctx.request().getFormAttribute("term")),
                            Updates.set("year", ctx.request().getFormAttribute("year")),
                            Updates.set("fileid", newpdfid)

                    );

                    UpdateResult result2 = pdfdb.updateOne(filter, update);
                    if(result2.getModifiedCount()!=0){
                        JsonObject job=new JsonObject().put("message","Updated successfully");
                        ctx.response().setStatusCode(200).end(job.encode());
                        return;
                    }
                    else{
                        JsonObject job=new JsonObject().put("message","Update Failed");
                        ctx.response().setStatusCode(400).end(job.encode());
                        return;
                    }



                }
                else{
                    ctx.response().setStatusCode(400).end("Unsupported Content Type.");
                    return;
                }

            }

        }else{
            JsonObject job=new JsonObject().put("message","Expired or Invalid");
            ctx.response().setStatusCode(401).end(job.encode());
            return;
        }
        for (FileUpload upload : ctx.fileUploads()) {
            try {
                Files.deleteIfExists(Paths.get(upload.uploadedFileName()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void deleterecord(RoutingContext ctx){


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
