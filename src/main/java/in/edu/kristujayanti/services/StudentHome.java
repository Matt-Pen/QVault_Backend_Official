package in.edu.kristujayanti.services;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class StudentHome extends AbstractVerticle {
    Authentication auth= new Authentication();
    JwtUtil jtil = new JwtUtil();
    secretclass srt = new secretclass();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("QVault");
    MongoCollection<Document> pdfdb = database.getCollection("QuestionPapers");
    MongoCollection<Document> usersdb = database.getCollection("Users");
    MongoCollection<Document> reqdb = database.getCollection("Requests");

    public void studenthome2(RoutingContext ctx){
        ctx.response().setChunked(true);
        if (auth.JWTauthguest(ctx)) {
            System.out.println("Success student home validation.");
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            String role = jtil.extractRole(token);

            Document master_response = new Document();
//            List<String> coursenames = pdfdb.distinct("course", String.class).into(new ArrayList<>());
//            List<String> courseids = pdfdb.distinct("courseid", String.class).into(new ArrayList<>());

            FindIterable<Document> course = pdfdb.find()
                    .projection(Projections.include("courseid", "course"));

            List<String> combinedCourses = new ArrayList<>();

            for (Document doc : course) {

                String courseId = doc.getString("courseid");
                String courseName = doc.getString("course");

                String combined = courseId + " - " + courseName;
                if(combinedCourses.contains(combined)){
                    continue;
                }
                combinedCourses.add(combined);
            }


            List<String> types = pdfdb.distinct("type", String.class).into(new ArrayList<>());
            List<String> terms = pdfdb.distinct("term", String.class).into(new ArrayList<>());
            List<String> year = pdfdb.distinct("year", String.class).into(new ArrayList<>());

            master_response.append("courses",combinedCourses);
            master_response.append("types", types);
            master_response.append("terms", terms);
            master_response.append("year", year);

            Document userDoc = usersdb.find(Filters.eq("email", email))
                    .projection(Projections.include("recents", "favourites"))
                    .first();


            List<ObjectId> recents = userDoc.getList("recents", ObjectId.class);
            JsonArray recentaccess = new JsonArray();
            if (!recents.isEmpty()) {
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
            master_response.append("recents", recentaccess);

            List<ObjectId> favs= userDoc.getList("favourites",ObjectId.class);
            int limit = Math.min(favs.size(), 6);
            JsonArray favpapers=new JsonArray();
            if(!favs.isEmpty()) {
                for (int i=0;i<limit;i++) {
                    ObjectId id= favs.get(i);
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
            master_response.append("favourites",favpapers);

            System.out.println("student home response sent.");
            JsonObject job = new JsonObject(master_response);

            ctx.response().end(job.encodePrettily());
        }

    }

    public void requestPaper(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if (auth.JWTauthguest(ctx)) {
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            JsonObject job = new JsonObject();

            JsonObject body = ctx.body().asJsonObject();
            String uname = body.getString("name");
            String course = body.getString("course");
            String crscode = body.getString("code");
            String year = body.getString("year");
            String sem = body.getString("sem");
            String prog=body.getString("program");
            String type=body.getString("type");
            String term = body.getString("term");
            String details = body.getString("details");
            String rollno = email.substring(0, email.indexOf('@'));
            String reqstatus = "pending";

            Document doc = new Document();
            doc.append("user", uname)
                    .append("rollno", rollno)
                    .append("email", email)
                    .append("course", course)
                    .append("courseid", crscode)
                    .append("year", year)
                    .append("sem", sem)
                    .append("program",prog)
                    .append("type",type)
                    .append("term", term)
                    .append("status", reqstatus);

            if (details != null) {
                doc.append("details", details);
            }
            InsertOneResult ins = reqdb.insertOne(doc);

            if (ins.wasAcknowledged()) {
                job.put("message", "success");
                Document stats = reqdb.find(Filters.eq("stats", "stats")).first();

//                    String  pend= stats.getString("pending");
//                    int pending = Integer.parseInt(pend);
//                    pending+=1;
                int pending = stats.getInteger("pending");
                pending += 1;

                Bson update = Updates.set("pending", pending);
                UpdateResult res = reqdb.updateOne(Filters.eq("stats", "stats"), update);
                System.out.println("Matched: " + res.getMatchedCount());
                System.out.println("Modified: " + res.getModifiedCount());
            } else {
                job.put("message", "failed");
            }
            ctx.response().end(job.encode());

        }
    }

    public void addtoFavorites(RoutingContext ctx) {
        ctx.response().setChunked(true);
        if (auth.JWTauthguest(ctx)) {
            System.out.println("in add favs");
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            JsonObject body = ctx.body().asJsonObject();
            ObjectId fileid = new ObjectId(body.getString("fileid"));
            Document matchdoc = usersdb.find(Filters.eq("email", email)).first();

            List<ObjectId> favs = matchdoc.getList("favourites", ObjectId.class);
            if (favs == null) {
                List<ObjectId> favs2 = new ArrayList<>();
                favs2.add(fileid);
                UpdateResult upd = usersdb.updateOne(
                        Filters.eq("email", email),
                        Updates.set("favourites", favs2));
                if (upd.getModifiedCount() > 0) {
                    System.out.println("add favs success");
                    ctx.response().setStatusCode(200).end(new JsonObject().put("message", "success").encode());
                    return;
                }
            } else {
                if(!favs.contains(fileid)) {
                    favs.add(0, fileid);

                    UpdateResult upd = usersdb.updateOne(
                            Filters.eq("email", email),
                            Updates.set("favourites", favs));
                    if (upd.getModifiedCount() > 0) {
                        System.out.println("add favs success");
                        ctx.response().setStatusCode(200).end(new JsonObject().put("message", "success").encode());
                        return;
                    }
                }
                else {
                    ctx.response().setStatusCode(200).end(new JsonObject().put("message", "exist").encode());
                    return;
                }
            }
            ctx.response().setStatusCode(400).end(new JsonObject().put("message", "Failed").encode());
        }
    }

    public void deletefromFavorites(RoutingContext ctx) {
        if (auth.JWTauthguest(ctx)) {
            System.out.println("in delete favs");
            String auth = ctx.request().getHeader("Authorization");
            String token = auth.replace("Bearer ", "");
            String email = jtil.extractEmail(token);
            JsonObject body = ctx.body().asJsonObject();
            ObjectId fileid = new ObjectId(body.getString("fileid"));

            Document matchdoc = usersdb.find(Filters.eq("email", email)).first();

            List<ObjectId> favs = matchdoc.getList("favourites", ObjectId.class);

            favs.remove(fileid);

            UpdateResult upd = usersdb.updateOne(
                    Filters.eq("email", email),
                    Updates.set("favourites", favs));
            if (upd.getModifiedCount() > 0) {
                System.out.println("delete favs success");
                ctx.response().setStatusCode(200).end(new JsonObject().put("message", "success").encode());
            } else {
                ctx.response().setStatusCode(400).end(new JsonObject().put("message", "Failed").encode());
            }
        }
    }

    public void showFavorites(RoutingContext ctx) {
        if (auth.JWTauthguest(ctx)) {
            System.out.println("in show favs");
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
                System.out.println("show favs success");
                ctx.response().setStatusCode(200).end(new JsonObject().put("favourites", favpapers).encodePrettily());
            } catch (Exception e) {
                ctx.response().setStatusCode(400).end(new JsonObject().put("message", "Failed").encode());
            }
        }

    }
}
