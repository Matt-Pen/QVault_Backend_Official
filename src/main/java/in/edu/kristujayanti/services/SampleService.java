package in.edu.kristujayanti.services;
import com.mongodb.client.*;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.AWSEmail;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import jakarta.mail.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import redis.clients.jedis.Jedis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

import io.vertx.core.AbstractVerticle;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import static in.edu.kristujayanti.handlers.SampleHandler.presigner;

public class SampleService extends AbstractVerticle {

    private final S3Client s3;

    public SampleService(S3Client s3) {
        this.s3 = s3;
    }

    JwtUtil jtil = new JwtUtil();

    Jedis jedis = new Jedis("localhost", 6379);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    secretclass srt = new secretclass();
    AWSEmail ses = new AWSEmail();
    Vertx vertx = Vertx.vertx();
    HttpServer server = vertx.createHttpServer();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("QVault");
    MongoCollection<Document> usersdb = database.getCollection("Users");
    MongoCollection<Document> wishdb = database.getCollection("wishlist");
    MongoCollection<Document> pdfdb = database.getCollection("QuestionPapers");
    MongoCollection<Document> reqdb = database.getCollection("Requests");



    public void usersignup(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body.getString("email");
        String otp = body.getString("otp");
        String newpass = body.getString("password");
        String status = "";
        System.out.println("in user Sign");
        Document docs = usersdb.find().filter(Filters.eq("email", email)).first();
        if (docs != null) {
            status = "exist";
        } else {
            if (otp == null && newpass == null && email != null) {
                String send_otp = generateID(6);
                System.out.println(send_otp);
                setotp(send_otp, email);
                ses.sendawssignup(send_otp, email);
                status = "OTP Sent";
            } else if (email != null && otp != null && newpass != null) {
                String sent_otp = getoken(otp);
                if (sent_otp.equals(email)) {
                    if (email.matches(".*\\d.*") && email.contains("@kristujayanti.com")) {
                        String role = "Guest";
                        String designation = "Student";
                        String rollno = email.substring(0, email.indexOf('@'));
                        String hashpass = hashPassword(newpass);
                        System.out.println(hashpass);
                        Document insdoc = new Document("email", email).append("pass", hashpass).append("role", role).append("designation", designation).append("rollno",rollno).append("recents", new ArrayList<ObjectId>()).append("favourites", new ArrayList<ObjectId>());
                        InsertOneResult insres = usersdb.insertOne(insdoc);
                        if (insres.wasAcknowledged()) {
                            status = "success";
                            deltoken(otp);
                        } else {
                            deltoken(otp);
                        }
                    } else if (email.contains("@kristujayanti.com")) {
                        String role = "Admin";
                        String designation = "Faculty";
                        String hashpass = hashPassword(newpass);
                        System.out.println(hashpass);
                        String rollno = email.substring(0, email.indexOf('@'));
                        Document insdoc = new Document("email", email).append("pass", hashpass).append("role", role).append("designation", designation).append("rollno",rollno).append("recents", new ArrayList<ObjectId>()).append("favourites", new ArrayList<ObjectId>());
                        InsertOneResult insres = usersdb.insertOne(insdoc);
                        if (insres.wasAcknowledged()) {
                            status = "success";
                            deltoken(otp);
                        } else {
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

        JsonObject job = new JsonObject().put("message", status);
        ctx.response().end(job.encode());
    }

    public void userlogin(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body.getString("email");
        String pass = body.getString("password");

        String acctoken = "";
        String reftoken = "";
        String status = "";
        String desig="";
        String role="";

        System.out.println("In Login");
        String token2 = jedis.get("jwt:ref" + email);
        if (token2 != null) {
            deltoken("jwt:acc" + email);
            deltoken("jwt:ref" + email);

        }

        Bson filt1 = Filters.eq("email", email);
        Document matchdoc = usersdb.find(filt1).first();
        if (matchdoc == null) {
            status = "invalid username";
        } else {
            String dbpass = matchdoc.getString("pass");

            if (verifyPassword(pass, dbpass)) {
                status = "Login success";
                String dbrole = matchdoc.getString("role");
                acctoken = jtil.generateAccessToken(email, 30, dbrole);
                reftoken = jtil.generateRefreshToken(email, 7, dbrole);
                setoken("jwt:ref" + email, reftoken);
                desig=matchdoc.getString("designation");
                role=matchdoc.getString("role");
                System.out.println(getoken("jwt:ref" + email));
            } else {
                status = "invalid password";
            }

        }
        JsonObject job = new JsonObject().put("message", status).put("access token", acctoken).put("refresh token", reftoken).put("designation",desig).put("role",role);
        ctx.response().end(job.encodePrettily());
    }

    public void resetpassword(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body.getString("email");
        String otp = body.getString("otp");
        String pass = body.getString("password");

        String status = "";
        if (otp == null && pass == null) {
            String send_otp = generateID(6);
            System.out.println(send_otp);
            setotp(send_otp, email);
            ses.sendawsforgotpass(send_otp, email);
//            sendresetmail(send_otp, email);
            status = "OTP Sent";

        } else if (email != null && otp != null & pass != null) {
            String sent_otp = getoken(otp);
            if (sent_otp.equals(email)) {
                String hashpass = hashPassword(pass);
                Bson filter = Filters.eq("email", email);
                Bson update = Updates.set("pass", hashpass);
                UpdateResult res = usersdb.updateOne(filter, update);
                if (res.wasAcknowledged()) {
                    status = "success";
                    deltoken(sent_otp);
                } else {
                    status = "update failed";
                }
            }
        } else {
            status = "invalid information";
        }
        JsonObject job = new JsonObject().put("message", status);
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
        if(JWTauthadmin(ctx)){
          System.out.println("role and user valid valid succes");


                String name = ctx.request().getFormAttribute("course");
                String courseid = ctx.request().getFormAttribute("id");
                String department = ctx.request().getFormAttribute("department");
                String courseName = ctx.request().getFormAttribute("program");
                String examTerm = ctx.request().getFormAttribute("term");
                String year = ctx.request().getFormAttribute("year");
                String type = ctx.request().getFormAttribute("type");
                String sem=ctx.request().getFormAttribute("sem");
                JsonObject job = new JsonObject();
                ObjectId fileid = null;

                try {

                    GridFSBucket gridFSBucket = GridFSBuckets.create(database);

                    for (FileUpload upload : ctx.fileUploads()) {
                        try {
                            if (upload.contentType().equals("application/pdf")) {
                                Path filePath = Paths.get(upload.uploadedFileName());
                                try (InputStream pdfStream = Files.newInputStream(filePath)) {
                                    fileid = gridFSBucket.uploadFromStream(upload.fileName(), pdfStream);

                                }
                            } else {
                                System.out.println("Skipping non-PDF file: " + upload.fileName());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    Document doc = new Document();
                    doc.append("course", name)
                            .append("courseid", courseid)
                            .append("department", department)
                            .append("program", courseName)
                            .append("sem",sem)
                            .append("type", type)
                            .append("term", examTerm)
                            .append("year", year)
                            .append("fileid", fileid);

                    for (FileUpload upload : ctx.fileUploads()) {
                        try {
                            Files.deleteIfExists(Paths.get(upload.uploadedFileName()));
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


        }
        for (FileUpload upload : ctx.fileUploads()) {
            try {
                Files.deleteIfExists(Paths.get(upload.uploadedFileName()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public void handleupdate(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if(JWTauthadmin(ctx)){
             System.out.println("role and user valid valid succes");
                String contentType = ctx.request().getHeader("Content-Type");
                if (contentType != null && contentType.contains("application/json")) {
                    JsonObject body = ctx.body().asJsonObject();
                    ObjectId id = new ObjectId(body.getString("id"));
                    Bson filter = Filters.eq("_id", id);
                    Bson update = Updates.combine(
                            Updates.set("course", body.getString("course")),
                            Updates.set("courseid", body.getString("courseid")),
                            Updates.set("department", body.getString("department")),
                            Updates.set("program", body.getString("program")),
                            Updates.set("type", body.getString("type")),
                            Updates.set("term", body.getString("term")),
                            Updates.set("year", body.getString("year")),
                            Updates.set("sem", body.getString("sem"))
                    );

                    UpdateResult result2 = pdfdb.updateOne(filter, update);
                    if (result2.getModifiedCount() != 0) {
                        JsonObject job = new JsonObject().put("message", "Updated successfully");
                        ctx.response().setStatusCode(200).end(job.encode());
                        return;
                    } else {
                        JsonObject job = new JsonObject().put("message", "Update Failed");
                        ctx.response().setStatusCode(400).end(job.encode());
                        return;
                    }


                } else if (contentType != null && contentType.contains("multipart/form-data")) {
                    ObjectId id = new ObjectId(ctx.request().getFormAttribute("id"));
                    ObjectId pdfid = new ObjectId(ctx.request().getFormAttribute("fileid"));


                    ObjectId newpdfid = null;
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
                    try {
                        gridFSBucket.delete(pdfid);
                    } catch (Exception e) {
                        JsonObject job = new JsonObject().put("message", "Update Failed");
                        ctx.response().setStatusCode(400).end(job.encode());
                        return;
                    }


                    Bson filter = Filters.eq("_id", id);
                    Bson update = Updates.combine(
                            Updates.set("course", ctx.request().getFormAttribute("course")),
                            Updates.set("courseid", ctx.request().getFormAttribute("courseid")),
                            Updates.set("department", ctx.request().getFormAttribute("department")),
                            Updates.set("program", ctx.request().getFormAttribute("program")),
                            Updates.set("type", ctx.request().getFormAttribute("type")),
                            Updates.set("term", ctx.request().getFormAttribute("term")),
                            Updates.set("year", ctx.request().getFormAttribute("year")),
                            Updates.set("sem", ctx.request().getFormAttribute("sem")),
                            Updates.set("fileid", newpdfid)

                    );

                    UpdateResult result2 = pdfdb.updateOne(filter, update);
                    if (result2.getModifiedCount() != 0) {
                        JsonObject job = new JsonObject().put("message", "Updated successfully");
                        ctx.response().setStatusCode(200).end(job.encode());
                        return;
                    } else {
                        JsonObject job = new JsonObject().put("message", "Update Failed");
                        ctx.response().setStatusCode(400).end(job.encode());
                        return;
                    }


                } else {
                    ctx.response().setStatusCode(400).end("Unsupported Content Type.");
                    return;
                }


        }
        for (FileUpload upload : ctx.fileUploads()) {
            try {
                Files.deleteIfExists(Paths.get(upload.uploadedFileName()));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public void deleterecord(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if(JWTauthadmin(ctx)){

            System.out.println("role and user valid valid succes");
                JsonObject body = ctx.body().asJsonObject();
                ObjectId id = new ObjectId(body.getString("id"));

                GridFSBucket gridFSBucket = GridFSBuckets.create(database);
                Document doc = pdfdb.find(Filters.eq("_id", id)).first();
                if (doc == null) {
                    JsonObject job = new JsonObject().put("message", "Record not found");
                    ctx.response().setStatusCode(400).end(job.encode());
                    return;
                }

                try {
                    ObjectId pdfid = doc.getObjectId("fileid");
                    gridFSBucket.delete(pdfid);

                } catch (Exception e) {
                    e.printStackTrace();
                    JsonObject job = new JsonObject().put("message", "PDF Grif Fs Delete Failed");
                    ctx.response().setStatusCode(400).end(job.encode());
                    return;

                }

                DeleteResult result = pdfdb.deleteOne(Filters.eq("_id", id));
                if (result.getDeletedCount() > 0) {
                    JsonObject job = new JsonObject().put("message", "Deleted Successfully");
                    ctx.response().setStatusCode(200).end(job.encode());
                    return;
                } else {
                    JsonObject job = new JsonObject().put("message", "Delete Failed");
                    ctx.response().setStatusCode(500).end(job.encode());
                    return;
                }
            }
    }


    public void getpdfbyid2(RoutingContext ctx) {
        if (JWTauthguest(ctx)) {
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            String role = jtil.extractRole(token);
            Bson filt1 = Filters.eq("email", email);
            Document matchdoc = usersdb.find(filt1).first();

            JsonObject body = ctx.body().asJsonObject();
            ObjectId pdfid = new ObjectId(body.getString("fileid"));
            try {
                GridFSBucket gridFSBucket = GridFSBuckets.create(database);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                gridFSBucket.downloadToStream(pdfid, outputStream);
                byte[] pdfBytes = outputStream.toByteArray();

                ctx.response()
                        .putHeader("Content-Type", "application/pdf")
                        .putHeader("Content-Disposition", "inline; filename=\"questionpaper.pdf\"")
                        .putHeader("Content-Length", String.valueOf(pdfBytes.length))
                        .end(Buffer.buffer(pdfBytes));

            } catch (Exception e) {
                JsonObject job = new JsonObject().put("message", "Pdf View Failed");
                ctx.response().setStatusCode(500).end(job.encode());
                return;
            }

            List<ObjectId> recents = matchdoc.getList("recents", ObjectId.class);
            if (recents == null) {
                List<ObjectId> recents2 = new ArrayList<>();
                recents2.add(pdfid);
                Bson update = Updates.set("recents", recents2);
                UpdateResult result = usersdb.updateOne(Filters.eq("email", email), update);
                System.out.println("recents2" + recents2);

                if (result.getModifiedCount() > 0) {
                    System.out.println("recents inserted");
                } else {
                    System.out.println("recents insert FAILED");
                }


            } else {
                if (recents.size() < 8) {
                    recents.remove(pdfid);
                    recents.add(0, pdfid);


                } else if (recents.size() == 8) {
                    if (!recents.contains(pdfid)) {
                        recents.add(0, pdfid);
                        recents.remove(recents.size() - 1);
                    } else {
                        recents.remove(pdfid);
                        recents.add(0, pdfid);
                    }
                }
                Bson update = Updates.set("recents", recents);
                UpdateResult result = usersdb.updateOne(Filters.eq("email", email), update);
                System.out.println("recents " + recents);
                if (result.getModifiedCount() > 0) {
                    System.out.println("recents inserted");
                } else {
                    System.out.println("recents insert FAILED");
                }

            }

        }
}

    public void getpdfbyid3(RoutingContext ctx) {

        if (JWTauthguest(ctx)) {

            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);

            // fetch user
            Document matchdoc = usersdb.find(Filters.eq("email", email)).first();

            JsonObject body = ctx.body().asJsonObject();
            ObjectId pdfid = new ObjectId(body.getString("fileid"));

            try {
                // 🔹 fetch paper metadata
                Document paperDoc = pdfdb.find(Filters.eq("_id", pdfid)).first();

                if (paperDoc == null) {
                    ctx.response().setStatusCode(404)
                            .end(new JsonObject().put("message", "Paper not found").encode());
                    return;
                }

                String bucket = paperDoc.getString("bucket");
                String objectKey = paperDoc.getString("objectKey");

                // 🔹 build presigned URL
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build();

                GetObjectPresignRequest presignRequest =
                        GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(10))
                                .getObjectRequest(getObjectRequest)
                                .build();

                PresignedGetObjectRequest presignedRequest =
                        presigner.presignGetObject(presignRequest);

                String presignedUrl = presignedRequest.url().toString();

                // 🔹 send URL to frontend
                JsonObject response = new JsonObject()
                        .put("url", presignedUrl);

                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());

            } catch (Exception e) {
                e.printStackTrace();
                ctx.response().setStatusCode(500)
                        .end(new JsonObject().put("message", "Pdf View Failed").encode());
                return;
            }



            List<ObjectId> recents = matchdoc.getList("recents", ObjectId.class);

            if (recents == null) {
                List<ObjectId> recents2 = new ArrayList<>();
                recents2.add(pdfid);
                usersdb.updateOne(
                        Filters.eq("email", email),
                        Updates.set("recents", recents2)
                );
            } else {
                recents.remove(pdfid);
                recents.add(0, pdfid);

                if (recents.size() > 8) {
                    recents.remove(recents.size() - 1);
                }

                usersdb.updateOne(
                        Filters.eq("email", email),
                        Updates.set("recents", recents)
                );
            }
        }
    }


    public void searchfilter(RoutingContext ctx) {
        if (JWTauthguest(ctx)) {
            int perpage=3;
            JsonObject body = ctx.body().asJsonObject();
            String course=body.getString("course");
            String crscode=body.getString("code");
            String year=body.getString("year");
            String sess=body.getString("session");
            int page=body.getInteger("page");

            List<Bson> params=new ArrayList<>();
            if(course!=null && !course.isEmpty()){
                params.add(Filters.eq("course",course));
            }
            if(crscode!=null && !crscode.isEmpty()){
                params.add(Filters.eq("courseid",crscode));
            }
            if(year!=null && !year.isEmpty()){
                params.add(Filters.eq("year",year));
            }
            if(sess!=null && !sess.isEmpty()){
                params.add(Filters.eq("term",sess));
            }

            Bson filter = params.isEmpty() ? new Document() : Filters.and(params);
            JsonArray jarr=new JsonArray();
            for( Document docs : pdfdb.find(filter).skip(page*perpage).limit(perpage)){
                JsonObject json = new JsonObject();

                ObjectId id = docs.getObjectId("_id");
                json.put("_id", id.toHexString());

                for (String key : docs.keySet()) {
                    if (!(key.equals("_id") || key.equals("bucket") || key.equals("objectKey"))) {
                        json.put(key, docs.get(key));
                    }
                }
                jarr.add(json);

            }
            if(!jarr.isEmpty()) {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(jarr.encodePrettily());
            }
            else{
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("page", "end").encode());
            }

        }

    }
    public void studentHome(RoutingContext ctx){
        ctx.response().setChunked(true);
        if(JWTauthguest(ctx)){
            System.out.println("Success student home validation.");
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            String role = jtil.extractRole(token);

            Document master_response=new Document();
            //getting the drop-down list parameters
            List<String> coursenames=pdfdb.distinct("course",String.class).into(new ArrayList<>());
            List<String> courseids=pdfdb.distinct("courseid",String.class).into(new ArrayList<>());
            List<String> types=pdfdb.distinct("type",String.class).into(new ArrayList<>());
            List<String> terms=pdfdb.distinct("term",String.class).into(new ArrayList<>());
            List<String> program=pdfdb.distinct("program",String.class).into(new ArrayList<>());
            List<String> year =pdfdb.distinct("year",String.class).into(new ArrayList<>());

            master_response.append("courses",coursenames);
            master_response.append("courseids",courseids);
            master_response.append("types",types);
            master_response.append("terms",terms);
            master_response.append("program",program);
            master_response.append("year",year);

            Document userDoc = usersdb.find(Filters.eq("email", email))
                    .projection(Projections.include("recents","favourites"))
                    .first();

            List<Document> randomPapers = pdfdb.aggregate(
                    Arrays.asList(
                            Aggregates.sample(6)
                    )
            ).into(new ArrayList<>());

            JsonArray papersArray = new JsonArray();

            master_response.put("recommendedPapers", papersArray);

            for (Document doc : randomPapers    ) {
                JsonObject paperJson = new JsonObject();

                ObjectId obid = doc.getObjectId("_id");
                paperJson.put("_id", obid.toHexString());

                for (String key : doc.keySet()) {
                    if (!key.equals("_id")) {
                        paperJson.put(key, doc.get(key));
                    }
                }
                papersArray.add(paperJson);
            }
            master_response.put("recommendedPapers", papersArray);

            List<ObjectId> recents= userDoc.getList("recents",ObjectId.class);
            JsonArray recentaccess=new JsonArray();
            if(!recents.isEmpty()) {
                for (ObjectId id : recents) {
                    Document docs = pdfdb.find(Filters.eq("_id", id)).first();
                    JsonObject json = new JsonObject();

                    ObjectId obid = docs.getObjectId("_id");
                    json.put("_id", obid.toHexString());

                    for (String key : docs.keySet()) {
                        if (!(key.equals("_id") || key.equals("bucket") || key.equals("objectKey"))) {
                            json.put(key, docs.get(key));
                        }
                    }
                    recentaccess.add(json);
                }
            }
            master_response.append("recents",recentaccess);

//            List<ObjectId> favs= userDoc.getList("favourites",ObjectId.class);
//            JsonArray favpapers=new JsonArray();
//            if(!favs.isEmpty()) {
//                for (ObjectId id : favs) {
//                    Document docs = pdfdb.find(Filters.eq("_id", id)).projection(Projections.include("_id")).first();
//                    JsonObject json = new JsonObject();
//
//                    ObjectId obid = docs.getObjectId("_id");
//                    json.put("_id", obid.toHexString());
//
////                    for (String key : docs.keySet()) {
////                        if (!(key.equals("_id") || key.equals("bucket") || key.equals("objectKey"))) {
////                            json.put(key, docs.get(key));
////                        }
////                    }
//                    favpapers.add(json);
//                }
//            }
//
//            master_response.append("favourites",favpapers);

            JsonObject job= new JsonObject(master_response);
            ctx.response().end(job.encodePrettily());
        }
    }


    public void requestPaper(RoutingContext ctx){
        ctx.response().setChunked(true);
        if(JWTauthguest(ctx)){
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            JsonObject job = new JsonObject();

            JsonObject body = ctx.body().asJsonObject();
            String uname=body.getString("name");
            String course=body.getString("course");
            String crscode=body.getString("code");
            String year=body.getString("year");
            String sem=body.getString("sem");
            String term=body.getString("term");
            String details=body.getString("details");
            String rollno = email.substring(0, email.indexOf('@'));
            String reqstatus="pending";

            Document doc= new Document();
            doc.append("user",uname)
                    .append("rollno",rollno)
                    .append("email",email)
                    .append("course",course)
                    .append("courseid",crscode)
                    .append("year",year)
                    .append("sem",sem)
                    .append("term",term)
                    .append("status",reqstatus);

            if(details!=null){
                doc.append("details",details);
            }
            InsertOneResult ins=reqdb.insertOne(doc);

            if (ins.wasAcknowledged()){
                job.put("message","success");
                Document stats= reqdb.find(Filters.eq("stats","stats")).first();

//                    String  pend= stats.getString("pending");
//                    int pending = Integer.parseInt(pend);
//                    pending+=1;
                int pending=stats.getInteger("pending");
                pending+=1;

                Bson update=Updates.set("pending",pending);
                UpdateResult res=reqdb.updateOne(Filters.eq("stats","stats"),update);
                System.out.println("Matched: " + res.getMatchedCount());
                System.out.println("Modified: " + res.getModifiedCount());
            }else{
                job.put("message","failed");
            }
            ctx.response().end(job.encode());

        }
    }

    public void requestpaperstatus(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if (JWTauthguest(ctx)) {
            JsonObject body = ctx.body().asJsonObject();
            ObjectId id = new ObjectId(body.getString("id"));
            String status = body.getString("status");
            Document items = new Document();
            Document reqinfo = reqdb.find(Filters.eq("_id", id)).first();
            JsonObject job= new JsonObject();


            String remarks = reqinfo.getString("details");
            if (remarks == null || remarks.isEmpty()) {
                remarks = "NONE";
            }
            String requestStatus;
            String statusColor;
            if ("approve".equals(status)) {
                requestStatus = "Approved";
                statusColor = "#3ccb34";
            } else if ("rejected".equals(status)) {
                requestStatus = "Rejected";
                statusColor = "#ff1818";
            } else {
                ctx.response().setStatusCode(400).end("Invalid status");
                return;
            }


            items.append("studentName", reqinfo.getString("user"))
                .append("requeststatus", requestStatus)
                .append("statuscolor", statusColor)
                .append("courseName", reqinfo.getString("course"))
                .append("courseId", reqinfo.getString("courseid"))
                .append("year", reqinfo.getString("year"))
                .append("semester", reqinfo.getString("sem"))
                .append("term", reqinfo.getString("term"))
                .append("remarks", remarks);

            Document stats= reqdb.find(Filters.eq("stats","stats")).first();
            int approve= stats.getInteger("approved");
            approve+=1;

            int pending=stats.getInteger("pending");
            pending-=1;

            Bson update=Updates.combine(Updates.set("pending",pending),Updates.set("approved",approve));
            UpdateResult res1=reqdb.updateOne(Filters.eq("stats","stats"),update);

            DeleteResult res=reqdb.deleteOne(Filters.eq("_id",id));

            if(res.wasAcknowledged() && res1.wasAcknowledged()){
                ses.sendrequeststatus(reqinfo.getString("email"),items);
                job.put("message","success");
                ctx.response().end(job.encode());
            }else{
                job.put("message","failed to delete or update");
                ctx.response().end(job.encode());
            }



        }
    }


    public void  handleuploadS3(RoutingContext ctx){
        ctx.response().setChunked(true);

        if (JWTauthadmin(ctx)) {
            System.out.println("role and user valid valid succes");

            String name = ctx.request().getFormAttribute("course");
            String courseid = ctx.request().getFormAttribute("id");
            String department = ctx.request().getFormAttribute("department");
            String courseName = ctx.request().getFormAttribute("program");
            String examTerm = ctx.request().getFormAttribute("term");
            String year = ctx.request().getFormAttribute("year");
            String type = ctx.request().getFormAttribute("type");
            String sem = ctx.request().getFormAttribute("sem");

            JsonObject job = new JsonObject();

            try {
                String objectKey = null;

                for (FileUpload upload : ctx.fileUploads()) {
                    try {
                        if ("application/pdf".equals(upload.contentType())) {

                            Path filePath = Paths.get(upload.uploadedFileName());

                            // SAME key = automatic replace
                            objectKey = courseid + "/"
                                            + department + "/"
                                            + sem + "/"
                                            + year + "/"
                                            + upload.fileName();

                            try (InputStream pdfStream = Files.newInputStream(filePath)) {

                                PutObjectRequest putRequest = PutObjectRequest.builder()
                                        .bucket("qvault-question-papers")
                                        .key(objectKey)
                                        .contentType("application/pdf")
                                        .build();

                                s3.putObject(
                                        putRequest,
                                        RequestBody.fromInputStream(pdfStream, Files.size(filePath))
                                );
                            }

                        } else {
                            System.out.println("Skipping non-PDF file: " + upload.fileName());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


                Document doc = new Document();
                doc.append("course", name)
                        .append("courseid", courseid)
                        .append("department", department)
                        .append("program", courseName)
                        .append("sem", sem)
                        .append("type", type)
                        .append("term", examTerm)
                        .append("year", year)
                        .append("bucket", "qvault-question-papers")
                        .append("objectKey", objectKey);


                for (FileUpload upload : ctx.fileUploads()) {
                    try {
                        Files.deleteIfExists(Paths.get(upload.uploadedFileName()));
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
                ctx.response().setStatusCode(500).end("Failed to upload PDF to S3");
            }
        }

    }

    public void handleupdateS3(RoutingContext ctx) {
        ctx.response().setChunked(true);

        if (!JWTauthadmin(ctx)) {
            ctx.response().setStatusCode(401).end("Unauthorized");
            return;
        }

        String contentType = ctx.request().getHeader("Content-Type");

        /* ================= JSON-ONLY METADATA UPDATE ================= */
        if (contentType != null && contentType.contains("application/json")) {

            JsonObject body = ctx.body().asJsonObject();
            ObjectId id = new ObjectId(body.getString("id"));

            Bson filter = Filters.eq("_id", id);
            Bson update = Updates.combine(
                    Updates.set("course", body.getString("course")),
                    Updates.set("courseid", body.getString("courseid")),
                    Updates.set("department", body.getString("department")),
                    Updates.set("program", body.getString("program")),
                    Updates.set("type", body.getString("type")),
                    Updates.set("term", body.getString("term")),
                    Updates.set("year", body.getString("year")),
                    Updates.set("sem", body.getString("sem"))
            );

            UpdateResult result = pdfdb.updateOne(filter, update);

            ctx.response()
                    .setStatusCode(result.getModifiedCount() > 0 ? 200 : 400)
                    .end(new JsonObject()
                            .put("message", result.getModifiedCount() > 0
                                    ? "Updated successfully"
                                    : "Update failed")
                            .encode());
            return;
        }

        /* ================= PDF UPDATE (S3) ================= */
        if (contentType != null && contentType.contains("multipart/form-data")) {

            ObjectId id = new ObjectId(ctx.request().getFormAttribute("id"));

            Document paper = pdfdb.find(Filters.eq("_id", id)).first();

            String bucket = paper.getString("bucket");
            String oldObjectKey = paper.getString("objectKey");

            String courseId = ctx.request().getFormAttribute("courseid");
            String department = ctx.request().getFormAttribute("department");
            String sem = ctx.request().getFormAttribute("sem");
            String year = ctx.request().getFormAttribute("year");

            String newObjectKey = null;

            for (FileUpload upload : ctx.fileUploads()) {

                if (!"application/pdf".equals(upload.contentType())) {
                    continue;
                }

                try {
                    Path filePath = Paths.get(upload.uploadedFileName());

                    // build structured S3 path
                    newObjectKey = courseId + "/"
                            + department + "/"
                            + sem + "/"
                            + year + "/"
                            + upload.fileName();

                    PutObjectRequest putReq = PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(newObjectKey)
                            .contentType("application/pdf")
                            .build();

                    s3.putObject(putReq, filePath);

                } catch (Exception e) {
                    e.printStackTrace();
                    ctx.response().setStatusCode(500)
                            .end(new JsonObject().put("message", "S3 upload failed").encode());
                    return;
                }
            }

            if (newObjectKey == null) {
                ctx.response().setStatusCode(400)
                        .end(new JsonObject().put("message", "No valid PDF uploaded").encode());
                return;
            }

            /* ---------- delete old S3 object ---------- */
            try {
                s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(oldObjectKey)
                        .build());
            } catch (Exception e) {
                e.printStackTrace();
                ctx.response().setStatusCode(500)
                        .end(new JsonObject().put("message", "Old file delete failed").encode());
                return;
            }

            Bson filter = Filters.eq("_id", id);
            Bson update = Updates.combine(
                    Updates.set("course", ctx.request().getFormAttribute("course")),
                    Updates.set("courseid", courseId),
                    Updates.set("department", department),
                    Updates.set("program", ctx.request().getFormAttribute("program")),
                    Updates.set("type", ctx.request().getFormAttribute("type")),
                    Updates.set("term", ctx.request().getFormAttribute("term")),
                    Updates.set("year", year),
                    Updates.set("sem", sem),
                    Updates.set("bucket", bucket),
                    Updates.set("objectKey", newObjectKey)
            );

            for (FileUpload upload : ctx.fileUploads()) {
                try {
                    Files.deleteIfExists(Paths.get(upload.uploadedFileName()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            UpdateResult result = pdfdb.updateOne(filter, update);

            ctx.response()
                    .setStatusCode(result.getModifiedCount() > 0 ? 200 : 400)
                    .end(new JsonObject()
                            .put("message", result.getModifiedCount() > 0
                                    ? "Updated successfully"
                                    : "Update failed")
                            .encode());
            return;
        }

        ctx.response().setStatusCode(400).end("Unsupported Content Type");
    }

    public void handledeleteS3(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if(JWTauthadmin(ctx)){
            JsonObject body = ctx.body().asJsonObject();
            ObjectId id = new ObjectId(body.getString("id"));
            Bson filter = Filters.eq("_id", id);

            Document paper = pdfdb.find(filter).first();
            if (paper == null) {
                ctx.response().setStatusCode(404)
                        .end(new JsonObject().put("message", "Record not found").encode());
                return;
            }


            String bucket = paper.getString("bucket");
            String oldObjectKey = paper.getString("objectKey");

            if (bucket == null || oldObjectKey == null) {
                ctx.response().setStatusCode(500)
                        .end(new JsonObject().put("message", "Invalid S3 metadata").encode());
                return;
            }


            try{
                s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(oldObjectKey)
                        .build());

            }catch(Exception e){
                e.printStackTrace();
                ctx.response().setStatusCode(500)
                        .end(new JsonObject().put("message", "S3 Delete Failed").encode());
                return;
            }

            DeleteResult del=pdfdb.deleteOne(filter);

            if(del.getDeletedCount()>0){
                ctx.response().setStatusCode(200)
                        .end(new JsonObject().put("message", "success").encode());
            }else{
                ctx.response().setStatusCode(400)
                        .end(new JsonObject().put("message", "failed").encode());
            }


        }
    }


    public void addtoFavorites(RoutingContext ctx){
        ctx.response().setChunked(true);
        if(JWTauthguest(ctx)){
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            JsonObject body= ctx.body().asJsonObject();
            ObjectId fileid= new ObjectId(body.getString("fileid"));
            Document matchdoc = usersdb.find(Filters.eq("email", email)).first();

            List<ObjectId> favs=matchdoc.getList("favourites",ObjectId.class);
            if(favs==null){
                List<ObjectId> favs2 = new ArrayList<>();
                favs2.add(fileid);
                UpdateResult upd=usersdb.updateOne(
                        Filters.eq("email", email),
                        Updates.set("favourites", favs2));
                if(upd.getModifiedCount()>0){
                    ctx.response().setStatusCode(200).end(new JsonObject().put("message","success").encode());
                }
            }else{
                favs.add(0,fileid);

                UpdateResult upd=usersdb.updateOne(
                        Filters.eq("email", email),
                        Updates.set("favourites", favs));
                if(upd.getModifiedCount()>0){
                    ctx.response().setStatusCode(200).end(new JsonObject().put("message","success").encode());
                }

            }
            ctx.response().setStatusCode(400).end(new JsonObject().put("message","Failed").encode());
        }
    }

    public void deletefromFavorites(RoutingContext ctx){
        if(JWTauthguest(ctx)){
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            JsonObject body= ctx.body().asJsonObject();
            ObjectId fileid= new ObjectId(body.getString("fileid"));

            Document matchdoc = usersdb.find(Filters.eq("email", email)).first();

            List<ObjectId> favs=matchdoc.getList("favourites",ObjectId.class);

            favs.remove(fileid);

            UpdateResult upd=usersdb.updateOne(
                    Filters.eq("email", email),
                    Updates.set("favourites", favs));
            if(upd.getModifiedCount()>0){
                ctx.response().setStatusCode(200).end(new JsonObject().put("message","success").encode());
            }
            else{
                ctx.response().setStatusCode(400).end(new JsonObject().put("message","Failed").encode());
            }
        }
    }

    public void showFavorites(RoutingContext ctx){
        if(JWTauthguest(ctx)) {
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            Document matchdoc = usersdb.find(Filters.eq("email", email)).first();

            try {
                List<ObjectId> favs = matchdoc.getList("favourites", ObjectId.class);
                JsonArray favpapers = new JsonArray();
                if (!favs.isEmpty()) {
                    for (ObjectId id : favs) {
                        Document docs = pdfdb.find(Filters.eq("_id", id)).first();
                        JsonObject json = new JsonObject();

                        ObjectId obid = docs.getObjectId("_id");
                        json.put("_id", obid.toHexString());

                        for (String key : docs.keySet()) {
                            if (!(key.equals("_id") || key.equals("bucket") || key.equals("objectKey"))) {
                                json.put(key, docs.get(key));
                            }
                        }
                        favpapers.add(json);
                    }
                }
                ctx.response().setStatusCode(200).end(new JsonObject().put("favorites", favpapers).encodePrettily());
            }catch (Exception e){
                ctx.response().setStatusCode(400).end(new JsonObject().put("message","Failed").encode());
            }
        }

    }


    public Boolean JWTauthguest(RoutingContext ctx){
        ctx.response().setChunked(true);
        String auth = ctx.request().getHeader("Authorization");

        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonObject job = new JsonObject().put("message", "Invalid Auth type");
            ctx.response().end(job.encode());
            System.out.println("auth null IF");
            return false;
        }

        String token = auth.replace("Bearer ", "");
        if (jtil.isTokenValid(token)) {
            System.out.println("token valid succes");
            String email = jtil.extractEmail(token);
            String role = jtil.extractRole(token);
            Bson filt1 = Filters.eq("email", email);
            Document matchdoc = usersdb.find(filt1).first();

            if (matchdoc != null && matchdoc.getString("email") != null && matchdoc.getString("role").equals(role) && (role.equals("Admin") || role.equals("Guest"))) {
                return true;

            }
            else {
                JsonObject job = new JsonObject().put("message", "Unauthorized access attempt.");
                ctx.response().setStatusCode(403).end(job.encode());
                return false;

            }

        } else {
            JsonObject job = new JsonObject().put("message", "Expired or Invalid");
            ctx.response().setStatusCode(401).end(job.encode());
            return false;
        }
    }
    public Boolean JWTauthadmin(RoutingContext ctx){
        ctx.response().setChunked(true);
        String auth = ctx.request().getHeader("Authorization");

        System.out.println("upload called");

        if (auth == null || !auth.startsWith("Bearer ")) {
            JsonObject job = new JsonObject().put("message", "Invalid Auth type");
            ctx.response().end(job.encode());
            System.out.println("auth null IF");
            return false;
        }

        String token = auth.replace("Bearer ", "");
        if (jtil.isTokenValid(token)) {
            System.out.println("token valid succes");
            String email = jtil.extractEmail(token);
            String role = jtil.extractRole(token);
            Bson filt1 = Filters.eq("email", email);
            Document matchdoc = usersdb.find(filt1).first();

            if (matchdoc != null && matchdoc.getString("email") != null && matchdoc.getString("role").equals(role) && (role.equals("Admin"))) {
                return true;

            }
            else {
                JsonObject job = new JsonObject().put("message", "Unauthorized access attempt.");
                ctx.response().setStatusCode(403).end(job.encode());
                return false;

            }

        } else {
            JsonObject job = new JsonObject().put("message", "Expired or Invalid");
            ctx.response().setStatusCode(401).end(job.encode());
            return false;
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
