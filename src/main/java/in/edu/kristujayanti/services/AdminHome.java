package in.edu.kristujayanti.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.AWSEmail;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class AdminHome {
    Authentication auth= new Authentication();
    JwtUtil jtil = new JwtUtil();
    AWSEmail ses=new AWSEmail();
    secretclass srt = new secretclass();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("QVault");
    MongoCollection<Document> pdfdb = database.getCollection("QuestionPapers");
    MongoCollection<Document> usersdb = database.getCollection("Users");
    MongoCollection<Document> reqdb = database.getCollection("Requests");

    public void adminhome(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if (auth.JWTauthadmin(ctx)) {
            System.out.println("Success Admin Home stats validation.");

            JsonObject master = new JsonObject();
            Document statdoc = reqdb.find(Filters.eq("stats", "stats")).first();

            long count = pdfdb.countDocuments();
            if (statdoc != null && count != 0) {
                master.put("countpapers", count);
                master.put("pending", statdoc.getInteger("pending").toString());
                master.put("approved", statdoc.getInteger("approved").toString());
                System.out.println("Admin home statr response sent.");
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(master.encodePrettily());

            } else {
                ctx.response().setStatusCode(400).end(new JsonObject().put("message", "failed").encode());
            }


        }
    }

    public void adminhomeqp(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if (auth.JWTauthadmin(ctx)) {
            System.out.println("Success Admin Home validation.");
            JsonObject body = ctx.body().asJsonObject();
            int perpage = 6;
            int page = body.getInteger("page");

            JsonArray jarr = new JsonArray();
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            for (Document docs : pdfdb.find().skip(page * perpage).limit(perpage)) {
                JsonObject json = new JsonObject();

                ObjectId id = docs.getObjectId("_id");
                json.put("_id", id.toHexString());

                for (String key : docs.keySet()) {
                    if (!(key.equals("_id") || key.equals("bucket") || key.equals("objectKey"))) {
                        if (key.equals("insertedate") || key.equals("updatedate")) {
                            Long millis = docs.getLong(key);
                            if (millis != null) {
                                json.put(key, sdf.format(new Date(millis)));
                            }
                        } else {
                            json.put(key, docs.get(key));
                        }
                    }
                }
                jarr.add(json);

            }
            JsonObject master = new JsonObject();
            master.put("papers", jarr);

            if (!jarr.isEmpty()) {
                System.out.println("Admin home Paper response sent.");
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(master.encodePrettily());
            }
        }
    }

    public void requestpaperstatus(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if (auth.JWTauthguest(ctx)) {
            JsonObject body = ctx.body().asJsonObject();
            ObjectId id = new ObjectId(body.getString("id"));
            String status = body.getString("status");
            Document items = new Document();
            Document reqinfo = reqdb.find(Filters.eq("_id", id)).first();
            JsonObject job = new JsonObject();


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

            Document stats = reqdb.find(Filters.eq("stats", "stats")).first();
            int approve = stats.getInteger("approved");
            approve += 1;

            int pending = stats.getInteger("pending");
            pending -= 1;

            Bson update = Updates.combine(Updates.set("pending", pending), Updates.set("approved", approve));
            UpdateResult res1 = reqdb.updateOne(Filters.eq("stats", "stats"), update);

            DeleteResult res = reqdb.deleteOne(Filters.eq("_id", id));

            if (res.wasAcknowledged() && res1.wasAcknowledged()) {
                ses.sendrequeststatus(reqinfo.getString("email"), items);
                job.put("message", "success");
                ctx.response().end(job.encode());
            } else {
                job.put("message", "failed to delete or update");
                ctx.response().end(job.encode());
            }


        }
    }


    public void adminrequestlist(RoutingContext ctx){
        ctx.response().setChunked(true);
        if (auth.JWTauthadmin(ctx)) {
            System.out.println("Success Admin request");
            JsonObject body = ctx.body().asJsonObject();
            int perpage = 1;
            int page = body.getInteger("page");

            JsonArray jarr = new JsonArray();
            Bson filter = Filters.ne("stats", "stats");
            for (Document docs : reqdb.find(filter).skip(page * perpage).limit(perpage)) {
                if(docs.getString("stats")!=null){
                    perpage+=1;
                    continue;
                }
                JsonObject json = new JsonObject();

                ObjectId id = docs.getObjectId("_id");
                json.put("_id", id.toHexString());

                for (String key : docs.keySet()) {
                    if (!(key.equals("_id"))) {
                        json.put(key, docs.get(key));
                    }
                }
                jarr.add(json);

            }
            JsonObject master = new JsonObject();
            master.put("requests", jarr);

            if (!jarr.isEmpty()) {
                System.out.println("Admin Requests list success");
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(master.encodePrettily());
            }
        }

    }

    public void createAdminuser(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if (auth.JWTauthSuperadmin(ctx)) {
            JsonObject body = ctx.body().asJsonObject();

            String email = body.getString("email");
            String role = "Admin";
            String designation = body.getString("designation");

            String hashpass = auth.hashPassword("1234");

            Document insdoc = new Document("email", email)
                    .append("pass", hashpass).append("role", role)
                    .append("designation", designation)
                    .append("status", "Active")
                    .append("recents", new ArrayList<ObjectId>())
                    .append("favourites", new ArrayList<ObjectId>());
            InsertOneResult insres = usersdb.insertOne(insdoc);
            if (insres.wasAcknowledged()) {
                ctx.response().setStatusCode(200).end(new JsonObject().put("message", "success").encodePrettily());
            } else {
                ctx.response().setStatusCode(400).end(new JsonObject().put("message", "Failed").encodePrettily());
            }


        }
    }

    public void changeAdminstatus(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if (auth.JWTauthSuperadmin(ctx)) {
            JsonObject body = ctx.body().asJsonObject();

            String email = body.getString("email");
            String status = body.getString("status");

            Bson filter = Filters.eq("email", email);
            Bson update = Updates.combine(
                    Updates.set("status", status));
            UpdateResult result = usersdb.updateOne(filter, update);

            if (result.getModifiedCount() > 0) {
                ctx.response().setStatusCode(200).end(new JsonObject().put("message", "success").encodePrettily());
            } else {
                ctx.response().setStatusCode(400).end(new JsonObject().put("message", "Failed").encodePrettily());
            }

        }
    }


}
