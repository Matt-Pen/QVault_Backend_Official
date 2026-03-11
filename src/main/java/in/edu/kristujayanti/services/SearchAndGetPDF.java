package in.edu.kristujayanti.services;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;

import jakarta.mail.*;
import java.util.*;

import static in.edu.kristujayanti.handlers.SampleHandler.presigner;

public class SearchAndGetPDF extends AbstractVerticle {

    Authentication auth= new Authentication();
    JwtUtil jtil = new JwtUtil();
    secretclass srt = new secretclass();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("QVault");
    MongoCollection<Document> pdfdb = database.getCollection("QuestionPapers");
    MongoCollection<Document> usersdb = database.getCollection("Users");

    public void searchfilternew(RoutingContext ctx){
        ctx.response().setChunked(true);
        if (auth.JWTauthguest(ctx)) {
            System.out.println("Success Search Filter new validation.");
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");

            System.out.println("In Search Filter");
            int perpage = 6;
            JsonObject body = ctx.body().asJsonObject();
            String course = body.getString("course");
            String year = body.getString("year");
            String sess = body.getString("term");
            int page = body.getInteger("page",0);

            List<Bson> params = new ArrayList<>();
            if (course != null && !course.isEmpty()) {
                String[] parts = course.split(" - ");

                String courseCode = parts[0];
                String courseName = parts[1];

                params.add(Filters.and(Filters.eq("course",courseName),
                        Filters.eq("courseid",courseCode)));
            }
            if (year != null && !year.isEmpty()) {
                params.add(Filters.eq("year", year));
            }
            if (sess != null && !sess.isEmpty()) {
                params.add(Filters.eq("term", sess));
            }

            Bson filter = params.isEmpty() ? new Document() : Filters.and(params);
            JsonArray jarr = new JsonArray();
            for (Document docs : pdfdb.find(filter).skip(page * perpage).limit(perpage)) {
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
            if (!jarr.isEmpty()) {
                System.out.println("Search Filter new response sent.");
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(jarr.encodePrettily());
            } else {
                System.out.println("Search filter new response Failed.");
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("page", "end").encode());
            }

        }

    }

    public void searchlist(RoutingContext ctx){
        ctx.response().setChunked(true);
        if (auth.JWTauthguest(ctx)) {
            System.out.println("Success Search List validation.");
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");

            JsonObject body = ctx.body().asJsonObject();
            String course = body.getString("course");
            String years = body.getString("year");
            String sess = body.getString("term");

            List<String> terms= new ArrayList<>();
            List<String> year= new ArrayList<>();
            if((course != null && !course.isEmpty()) && (sess != null && !sess.isEmpty())){
                String[] parts = course.split(" - ");

                String courseCode = parts[0];
                String courseName = parts[1];

                Bson filters=Filters.and(Filters.eq("course",courseName),
                        Filters.eq("courseid",courseCode),Filters.eq("term",sess));
                year = pdfdb.distinct("year", String.class).filter(filters).into(new ArrayList<>());


                Document master_response = new Document();
                master_response.append("years",year);

                System.out.println("Search List response sent.");
                JsonObject job = new JsonObject(master_response);
                ctx.response().end(job.encodePrettily());
                return;
            } else if ((course != null && !course.isEmpty()) && (years != null && !years.isEmpty())) {
                String[] parts = course.split(" - ");

                String courseCode = parts[0];
                String courseName = parts[1];

                Bson filters=Filters.and(Filters.eq("course",courseName),
                        Filters.eq("courseid",courseCode),Filters.eq("year",years));
                terms = pdfdb.distinct("term", String.class).filter(filters).into(new ArrayList<>());


                Document master_response = new Document();
                master_response.append("terms",terms);

                System.out.println("Search List response sent.");
                JsonObject job = new JsonObject(master_response);
                ctx.response().end(job.encodePrettily());
                return;
            } else if (course != null && !course.isEmpty()) {
                String[] parts = course.split(" - ");

                String courseCode = parts[0];
                String courseName = parts[1];

                Bson filters=Filters.and(Filters.eq("course",courseName),
                        Filters.eq("courseid",courseCode));
                terms = pdfdb.distinct("term", String.class).filter(filters).into(new ArrayList<>());
                year = pdfdb.distinct("year", String.class).filter(filters).into(new ArrayList<>());

                Document master_response = new Document();
                master_response.append("terms",terms);
                master_response.append("years",year);

                System.out.println("Search List response sent.");
                JsonObject job = new JsonObject(master_response);
                ctx.response().end(job.encodePrettily());
                return;

            }

        }
    }
    public void getpdfbyid3(RoutingContext ctx) {

        if (auth.JWTauthguest(ctx)) {
            System.out.println("in getpdf");

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


                System.out.println("getpdf success");
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

}
