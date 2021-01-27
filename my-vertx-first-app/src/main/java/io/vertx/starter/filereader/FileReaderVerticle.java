package io.vertx.starter.filereader;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.file.AsyncFile;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Tuple;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.starter.config.PgConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileReaderVerticle extends AbstractVerticle {

    static final int HTTP_PORT = 3002;
    static final String TEST_RESOURCES_PATH= "src"
            .concat(File.separator).concat("main")
            .concat(File.separator).concat("resources")
            .concat(File.separator);
    private static final Logger logger = LoggerFactory.getLogger(FileReaderVerticle.class);

    private PgPool pgPool;

    @Override
    public Completable rxStart() {
        pgPool = PgPool.pool(vertx, PgConfig.pgConnectOpts(), new PoolOptions());

        Router router = Router.router(vertx);
        router.get("/:users/loadNamesFile").handler(this::loadNamesFile);

        return vertx.createHttpServer()
                .requestHandler(router)
                .rxListen(HTTP_PORT)
                .ignoreElement();
    }

    private void loadNamesFile(RoutingContext ctx) {
        List<Tuple> names = new ArrayList<>();

        vertx.fileSystem().open(TEST_RESOURCES_PATH.concat("names.txt"), new OpenOptions(), result -> {
            if (result.succeeded()) {
                AsyncFile file = result.result();
                Flowable<io.vertx.reactivex.core.buffer.Buffer> observable = file.toFlowable();
                observable.flatMap(buffer -> Flowable.fromArray(buffer.toString().split("\n")))
                        .forEach(name-> {
                            names.add(Tuple.of(UUID.randomUUID().toString(),name));
                            //Tuple params = Tuple.of(UUID.randomUUID().toString(),name);
                            //pgPool
                            //        .preparedQuery(insertUsers())
                            //        .rxExecute(params);
                        });
                //pgPool.rxClose();
                observable.subscribe(
                        ok -> logger.info("File read correctly !! "),
                        err ->logger.error("An error occurred during read file: ", err));
                observable.doOnComplete(( () -> ctx.response().setStatusCode(200).end("Names found: " + names.size()) ));
            } else {
                System.err.println("Cannot open file " + result.cause());
            }
        });
    }
}
