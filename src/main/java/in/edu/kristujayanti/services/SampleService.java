package in.edu.kristujayanti.services;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import redis.clients.jedis.Jedis;

import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Random;

import io.vertx.core.AbstractVerticle;

public class SampleService extends AbstractVerticle {


    JwtUtil jtil = new JwtUtil();

    Jedis jedis = new Jedis("localhost", 6379);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    secretclass srt = new secretclass();
    Vertx vertx = Vertx.vertx();
    HttpServer server = vertx.createHttpServer();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("QVault");
    MongoCollection<Document> usersdb = database.getCollection("Users");
    MongoCollection<Document> wishdb = database.getCollection("wishlist");

    public void usersignup(RoutingContext ctx){
        JsonObject body=ctx.body().asJsonObject();
        String email=body.getString("email");
        String pass=body.getString("password");
        System.out.println(email);
        System.out.println(pass);
        String status="";
        ctx.response().setChunked(true);
        Document docs = usersdb.find().filter(Filters.eq("email", email)).first();

        if (docs!=null){
            status="exist";
        }
        else{
            if(email.matches(".*\\d.*") && email.contains("@kristujayanti.com")){
                String role="student";
                String hashpass= hashPassword(pass);
                System.out.println(hashpass);
                Document insdoc=new Document("email",email).append("pass",hashpass).append("role",role);
                InsertOneResult insres=usersdb.insertOne(insdoc);
                if(insres.wasAcknowledged()) {
                    status = "success";

                }
            } else if (email.contains("@kristujayanti.com")){
                String role="Admin";
                String hashpass= hashPassword(pass);
                System.out.println(hashpass);
                Document insdoc=new Document("email",email).append("pass",hashpass).append("role",role);
                InsertOneResult insres=usersdb.insertOne(insdoc);
                if(insres.wasAcknowledged()) {
                    status = "success";

                }
            }
            else{
                status="failed";
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
    public String getoken(String key){
        return jedis.get(key);
    }
    public void deltoken(String key){
        jedis.del(key);
    }

}
