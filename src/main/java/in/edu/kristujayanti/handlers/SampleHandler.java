package in.edu.kristujayanti.handlers;

import in.edu.kristujayanti.services.SampleService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public class SampleHandler extends AbstractVerticle {
    public static S3Presigner presigner;
    public void start(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer();
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        SampleService smp= new SampleService();


        router.post("/qvault/usersign").handler(smp::usersignup);
        router.post("/qvault/userlog").handler(smp::userlogin);
//        router.post("/logout").handler(smp::logout);
        router.post("/qvault/resetpass").handler(smp::resetpassword);
        router.post("/qvault/uploadQP").handler(smp::handleupload);
        router.put("/qvault/handleupdate").handler(smp::handleupdate);
        router.delete("/qvault/handledelete").handler(smp::deleterecord);

        router.get("/qvault/getpdf").handler(smp::getpdfbyid2);
        router.get("/qvault/search").handler(smp::searchfilter);

        router.get("/qvault/studenthome").handler(smp::studentHome);

        router.post("/qvault/requestpaper").handler(smp::requestPaper);
        router.post("/qvault/requeststatusupdate").handler(smp::requestpaperstatus);

        router.post("/qvault/uploadQPS3").handler(smp::handleuploadS3);
        router.get("/qvault/getpdfS3").handler(smp::getpdfbyid3);



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
