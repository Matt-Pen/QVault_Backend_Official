package in.edu.kristujayanti.handlers;

import in.edu.kristujayanti.services.SampleService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public class SampleHandler extends AbstractVerticle {
    public static S3Presigner presigner;
    public void start(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer();
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.PATCH)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization"));

        S3Client s3Client = S3Client.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        SampleService smp = new SampleService(s3Client);


        router.post("/qvault/usersign").handler(smp::usersignup);
        router.post("/qvault/userlog").handler(smp::userlogin);
//        router.post("/logout").handler(smp::logout);
        router.post("/qvault/resetpass").handler(smp::resetpassword);
        router.post("/qvault/uploadQP").handler(smp::handleupload);
        router.put("/qvault/handleupdate").handler(smp::handleupdate);
        router.delete("/qvault/handledelete").handler(smp::deleterecord);

        router.get("/qvault/getpdfold").handler(smp::getpdfbyid2);
        router.post("/qvault/searchfilter").handler(smp::searchfilter);

        router.get("/qvault/studenthome").handler(smp::studentHome);

        router.post("/qvault/requestpaper").handler(smp::requestPaper);
        router.post("/qvault/requeststatusupdate").handler(smp::requestpaperstatus);

        router.post("/qvault/uploadQPS3").handler(smp::handleuploadS3);
        router.get("/qvault/getpdf").handler(smp::getpdfbyid3);
        router.put("/qvault/handleupdateS3").handler(smp::handleupdateS3);
        router.delete("/qvault/handledeleteS3").handler(smp::handledeleteS3);
        router.get("/qvault/adminhome").handler(smp::adminhome);
        router.get("/qvault/adminhomeQP").handler(smp::adminhomeqp);

        router.post("/qvault/addFavs").handler(smp::addtoFavorites);
        router.put("/qvault/deleteFavs").handler(smp::deletefromFavorites);
        router.get("/qvault/showFavs").handler(smp::showFavorites);

        router.post("/qvault/createAdmin").handler(smp::createAdminuser);
        router.post("/qvault/Adminstatus").handler(smp::changeAdminstatus);


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
