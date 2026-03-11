package in.edu.kristujayanti.services;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import jakarta.mail.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import io.vertx.core.AbstractVerticle;

import software.amazon.awssdk.core.sync.RequestBody;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class CRUD extends AbstractVerticle {
    Authentication auth= new Authentication();
    JwtUtil jtil = new JwtUtil();
    private final S3Client s3;

    public CRUD(S3Client s3) {
        this.s3 = s3;
    }
    secretclass srt = new secretclass();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("QVault");
    MongoCollection<Document> pdfdb = database.getCollection("QuestionPapers");




    public void handleuploadS3(RoutingContext ctx) {
        ctx.response().setChunked(true);


        if (auth.JWTauthadmin(ctx)) {
            System.out.println("role and user valid valid succes");
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);

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
                        .append("insertedby", email)
                        .append("insertedate", System.currentTimeMillis())
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

        if (!auth.JWTauthadmin(ctx)) {
            ctx.response().setStatusCode(401).end("Unauthorized");
            return;
        }
        System.out.println("Update validation");
        String auth = ctx.request().getHeader("Authorization");
        String token = auth.replace("Bearer ", "");
        String email = jtil.extractEmail(token);

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
                    Updates.set("sem", body.getString("sem")),
                    Updates.set("updatedby", email),
                    Updates.set("updatedate", System.currentTimeMillis())
            );

            UpdateResult result = pdfdb.updateOne(filter, update);
            System.out.println("Updated meta data success");
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
                    Updates.set("updatedby", email),
                    Updates.set("updatedate", System.currentTimeMillis()),
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
            System.out.println("Updated meta data success");
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
        if (auth.JWTauthadmin(ctx)) {
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


            try {
                s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(oldObjectKey)
                        .build());

            } catch (Exception e) {
                e.printStackTrace();
                ctx.response().setStatusCode(500)
                        .end(new JsonObject().put("message", "S3 Delete Failed").encode());
                return;
            }

            DeleteResult del = pdfdb.deleteOne(filter);

            if (del.getDeletedCount() > 0) {
                ctx.response().setStatusCode(200)
                        .end(new JsonObject().put("message", "success").encode());
            } else {
                ctx.response().setStatusCode(400)
                        .end(new JsonObject().put("message", "failed").encode());
            }


        }
    }
}
