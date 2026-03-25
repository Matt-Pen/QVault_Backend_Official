package in.edu.kristujayanti.services;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.AWSEmail;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.mail.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import redis.clients.jedis.Jedis;

import java.util.*;

import io.vertx.core.AbstractVerticle;

public class Authentication extends AbstractVerticle {
    Jedis jedis = new Jedis("localhost", 6379);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    JwtUtil jtil = new JwtUtil();
    secretclass srt = new secretclass();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("QVault");
    MongoCollection<Document> usersdb = database.getCollection("Users");
    AWSEmail ses = new AWSEmail();



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
                        Document insdoc = new Document("email", email).append("pass", hashpass).append("role", role).append("designation", designation).append("rollno", rollno).append("recents", new ArrayList<ObjectId>()).append("favourites", new ArrayList<ObjectId>());
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
                        Document insdoc = new Document("email", email).append("pass", hashpass).append("role", role).append("designation", designation).append("rollno", rollno).append("recents", new ArrayList<ObjectId>()).append("favourites", new ArrayList<ObjectId>());
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
        String desig = "";
        String role = "";
        String userstatus="";
        String ustatus="";

        System.out.println("In Login");
        String token2 = jedis.get("jwt:ref" + email);
        if (token2 != null) {
            deltoken("jwt:acc" + email);
            deltoken("jwt:ref" + email);

        }

        Bson filt1 = Filters.eq("email", email);
        Document matchdoc = usersdb.find(filt1).first();
        userstatus=matchdoc.getString("status");
        if (matchdoc == null) {
            status = "invalid username";
        } else {
            String dbpass = matchdoc.getString("pass");

            if (verifyPassword(pass, dbpass)) {
                if(userstatus== null || userstatus.equals("Active")) {
                    status = "Login success";
                    String dbrole = matchdoc.getString("role");
                    acctoken = jtil.generateAccessToken(email, 30, dbrole);
                    reftoken = jtil.generateRefreshToken(email, 7, dbrole);
                    setoken("jwt:ref" + email, reftoken);
                    desig = matchdoc.getString("designation");
                    role = matchdoc.getString("role");

                    if (userstatus != null) {
                        ustatus=userstatus;
                    } else {
                        ustatus = "";
                    }
                    System.out.println(getoken("jwt:ref" + email));
                } else if (userstatus.equals("Inactive")) {
                    status="Deactivated Account";
                }
            } else {
                status = "invalid password";
            }

        }
        JsonObject job = new JsonObject().put("message", status)
                .put("access token", acctoken)
                .put("refresh token", reftoken)
                .put("designation", desig)
                .put("role", role)
                .put("status",ustatus);
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

            if (matchdoc != null) {
                String status = matchdoc.getString("status");
                if (matchdoc.getString("email") != null
                        && matchdoc.getString("role").equals(role)
                        && ((role.equals("Admin") && status.equals("Active")) || role.equals("SuperAdmin"))) {
                    return true;
                }else{
                    JsonObject job = new JsonObject().put("message", "Unauthorized access attempt.");
                    ctx.response().setStatusCode(403).end(job.encode());
                    return false;
                }
            }
            else {
                JsonObject job = new JsonObject().put("message", "Error");
                ctx.response().setStatusCode(500).end(job.encode());
                return false;
            }

        } else {
            JsonObject job = new JsonObject().put("message", "Expired or Invalid");
            ctx.response().setStatusCode(401).end(job.encode());
            return false;
        }
    }

    public Boolean JWTauthSuperadmin(RoutingContext ctx){
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

            if (matchdoc != null
                    && matchdoc.getString("email") != null
                    && matchdoc.getString("role").equals(role)
                    && (role.equals("SuperAdmin"))) {
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

    public Boolean JWTauthguest(RoutingContext ctx) {
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


            if (matchdoc != null) {
                String status = matchdoc.getString("status");
                if (matchdoc.getString("email") != null
                        && matchdoc.getString("role").equals(role)
                        && ((role.equals("Admin") && status.equals("Active")) || role.equals("Guest") || role.equals("SuperAdmin"))) {
                    return true;
                }else{
                    JsonObject job = new JsonObject().put("message", "Unauthorized access attempt.");
                    ctx.response().setStatusCode(403).end(job.encode());
                    return false;
                }

            } else {
                JsonObject job = new JsonObject().put("message", "Error");
                ctx.response().setStatusCode(500).end(job.encode());
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
