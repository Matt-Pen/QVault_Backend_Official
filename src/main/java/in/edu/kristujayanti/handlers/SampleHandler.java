package in.edu.kristujayanti.handlers;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import in.edu.kristujayanti.secretclass;
import in.edu.kristujayanti.services.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.bson.Document;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.concurrent.TimeUnit;

public class SampleHandler extends AbstractVerticle {
    public static S3Presigner presigner;
    public void start(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer();
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        secretclass srt = new secretclass();
        String connectionString = srt.constr;
        MongoClient mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase("QVault");
        MongoCollection<Document> reqdb = database.getCollection("Requests");
        reqdb.createIndex(
                new Document("deleteAt", 1),
                new IndexOptions().expireAfter(0L, TimeUnit.SECONDS)
        );


        router.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.PATCH)
                .allowedMethod(HttpMethod.PUT)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization"));

        S3Client s3Client = S3Client.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        SampleService smp = new SampleService(s3Client);
        Authentication auth=new Authentication();
        CRUD crud=new CRUD(s3Client);
        AdminHome admin= new AdminHome();
        StudentHome stud=new StudentHome();
        SearchAndGetPDF search=new SearchAndGetPDF();




//        router.post("/logout").handler(smp::logout);

        //Deprecated Endpoints
        router.post("/qvault/uploadQP").handler(smp::handleupload);
        router.put("/qvault/handleupdate").handler(smp::handleupdate);
        router.delete("/qvault/handledelete").handler(smp::deleterecord);
        router.get("/qvault/getpdfold").handler(smp::getpdfbyid2);
        router.post("/qvault/searchfilter").handler(smp::searchfilter);
        router.get("/qvault/studenthome").handler(smp::studentHome);



        //Active Endpoints
        router.post("/qvault/usersign").handler(auth::usersignup);
        router.post("/qvault/userlog").handler(auth::userlogin);
        router.post("/qvault/resetpass").handler(auth::resetpassword);

        router.get("/qvault/studenthome2").handler(stud::studenthome2);
        router.post("/qvault/searchfilternew").handler(search::searchfilternew);
        router.post("/qvault/searchlist").handler(search::searchlist);
        router.post("/qvault/requestpaper").handler(stud::requestPaper);
        router.post("/qvault/requeststatusupdate").handler(admin::requestpaperstatus);

        router.post("/qvault/uploadQPS3").handler(crud::handleuploadS3);
        router.post("/qvault/getpdf").handler(search::getpdfbyid3);
        router.put("/qvault/handleupdateS3").handler(crud::handleupdateS3);
        router.delete("/qvault/handledeleteS3").handler(crud::handledeleteS3);

        router.post("/qvault/adminhome").handler(admin::adminhome);
        router.post("/qvault/adminhomeQP").handler(admin::adminhomeqp);
        router.post("/qvault/adminrequests").handler(admin::adminrequestlist);

        router.post("/qvault/addFavs").handler(stud::addtoFavorites);
        router.post("/qvault/deleteFavs").handler(stud::deletefromFavorites);
        router.get("/qvault/showFavs").handler(stud::showFavorites);

        router.post("/qvault/createAdmin").handler(admin::createAdminuser);
        router.post("/qvault/Adminstatus").handler(admin::changeAdminstatus);
        router.post("/qvault/listAdmins").handler(admin::listAdmins);


        presigner = S3Presigner.builder()
                .region(Region.AP_SOUTH_1) // change if needed
                .build();


        Future<HttpServer> fut=server.requestHandler(router).listen(8080);
        if(fut.succeeded()){
            System.out.println("Server running at http://localhost:8080");
        }
        else{
            System.out.println("server failed to run.");
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        System.out.println("Server stopping...");
        stopPromise.complete();
    }

    //Handler Logic And Initialize the Service Here
}
