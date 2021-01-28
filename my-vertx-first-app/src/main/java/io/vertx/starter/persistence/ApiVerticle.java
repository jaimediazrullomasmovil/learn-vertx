package io.vertx.starter.persistence;

import io.reactivex.Completable;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.kafka.client.producer.KafkaProducer;
import io.vertx.reactivex.kafka.client.producer.KafkaProducerRecord;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Row;
import io.vertx.reactivex.sqlclient.RowSet;
import io.vertx.reactivex.sqlclient.Tuple;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.starter.config.KafkaConfig;
import io.vertx.starter.config.PgConfig;
import io.vertx.starter.kafka.EventType;

import java.util.UUID;

public class ApiVerticle extends AbstractVerticle {

    static final int HTTP_PORT = 3001;
    private static final Logger logger = LoggerFactory.getLogger(ApiVerticle.class);

    private PgPool pgPool;

    private KafkaProducer<String, JsonObject> producer;


    @Override
    public Completable rxStart() {
        pgPool = PgPool.pool(vertx, PgConfig.pgConnectOpts(), new PoolOptions());
        producer = KafkaProducer.create(vertx, KafkaConfig.producer());


        Router router = Router.router(vertx);
        router.get("/users").handler(this::allUsers);
        router.route("/users*").handler(BodyHandler.create());
        router.post("/users").handler(this::insertUser);
        router.get("/:users/:userId").handler(this::getOneUser);
        router.put("/users/:userId").handler(this::updateUser);
        router.delete("/users/:userId").handler(this::deleteUser);

        return vertx.createHttpServer()
                .requestHandler(router)
                .rxListen(HTTP_PORT)
                .ignoreElement();
    }

    private void getOneUser(RoutingContext ctx) {
        String id = ctx.request().getParam("userId");
        Tuple params = Tuple.of(id);
        pgPool
                .preparedQuery(SqlQueries.getOneById())
                .rxExecute(params)
                .subscribe(
                        row -> sendOneUser(ctx, row),
                        err -> handleError(ctx, err));
    }

    private void sendOneUser(RoutingContext ctx, RowSet<Row> rows) {
        if (rows.rowCount() == 1) {
            JsonObject payload = new JsonObject();
            rows.forEach(row -> payload.put("userId", row.getString("id"))
                    .put("userName", row.getString("name")));
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(payload.encode());
        } else {
            send404(ctx);
        }
    }

    private void deleteUser(RoutingContext ctx) {
        String id = ctx.request().getParam("userId");
        Tuple params = Tuple.of(id);
        pgPool
                .preparedQuery(SqlQueries.getOneById())
                .rxExecute(params)
                .subscribe(
                        rows -> sendDeleteUser(ctx, rows),
                        err -> handleError(ctx, err));
    }

    private void sendDeleteUser(RoutingContext ctx, RowSet<Row> rows) {
        if (rows.rowCount() == 1) {
            JsonObject payload = new JsonObject();
            rows.forEach(row -> payload.put("userId", row.getString("id"))
                    .put("userName", row.getString("name")));
            publishUserDelete(payload);
            ctx.response()
                    .setStatusCode(202)
                    .putHeader("Content-Type", "application/json")
                    .end(payload.encode());
        } else {
            send204(ctx);
        }
    }

    private void publishUserDelete(JsonObject data) {
        JsonObject event = new JsonObject().mergeIn(data);
        event.put("eventType", EventType.DELETE.toString());
        producer.rxSend(KafkaProducerRecord.create("incoming.users", data.getString("userId"), event))
                .subscribe(
                        ok -> logger.info("Message sent to topic !! " + ok.getTopic()),
                        err -> logger.error("An error occurred during sending message: ", err));
    }

    private void updateUser(RoutingContext ctx) {
        String id = ctx.request().getParam("userId");
        JsonObject json = ctx.getBodyAsJson();
        if (id == null || json == null) {
            ctx.response().setStatusCode(400).end();
        } else {
            Tuple params = Tuple.of(id);
            pgPool
                    .preparedQuery(SqlQueries.getOneById())
                    .rxExecute(params)
                    .subscribe(
                            rows -> {
                                if (rows.rowCount() == 1) {
                                    JsonObject payload = new JsonObject();
                                    rows.forEach(row -> {
                                        payload.put("userId", row.getString("id"))
                                                .put("userName", ctx.getBodyAsJson().getString("userName"));
                                    });
                                    publishUserUpdate(payload);
                                    ctx.response()
                                            .setStatusCode(200)
                                            .putHeader("Content-Type", "application/json")
                                            .end(payload.encode());
                                } else {
                                    send404(ctx);
                                }
                            },
                            err -> handleError(ctx, err));
        }
    }


    private void publishUserUpdate(JsonObject data) {
        JsonObject event = new JsonObject().mergeIn(data);
        event.put("eventType", EventType.UPDATE.toString());
        producer.rxSend(KafkaProducerRecord.create("incoming.users", data.getString("userId"), event))
                .subscribe(
                        ok -> logger.info("Message sent to topic !! " + ok.getTopic()),
                        err -> logger.error("An error occurred during sending message: ", err));
    }

    private void insertUser(RoutingContext ctx) {
        String userName = ctx.getBodyAsJson().getString("userName");
        if (userName == null) {
            ctx.response().setStatusCode(400).end();
        } else {
            JsonObject data = new JsonObject();
            String userId = UUID.randomUUID().toString();
            data.put("userId", userId);
            data.put("userName", userName);
            publishUserInsert(data);
            ctx.response()
                    .setStatusCode(202) //Async response
                    .putHeader("Content-Type", "application/json")
                    .end(data.encode());
        }
    }


    private void publishUserInsert(JsonObject data) {
        JsonObject event = new JsonObject().mergeIn(data);
        event.put("eventType", EventType.CREATE.toString());
        producer.rxSend(KafkaProducerRecord.create("incoming.users", data.getString("userId"), event))
                .subscribe(
                        ok -> logger.info("Message sent to topic [" + ok.getTopic() + "]!"),
                        err -> logger.error("An error occurred during sending message: ", err));
    }

    private void allUsers(RoutingContext ctx) {
        pgPool
                .preparedQuery(SqlQueries.totalUsers())
                .rxExecute()
                .subscribe(
                        rows -> sendAllUsers(ctx, rows),
                        err -> handleError(ctx, err));
    }

    private void sendAllUsers(RoutingContext ctx, RowSet<Row> rows) {
        JsonArray data = new JsonArray();
        for (Row row : rows) {
            data.add(new JsonObject()
                    .put("userId", row.getValue("id"))
                    .put("userName", row.getValue("name")));
        }
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(data.encode());
    }

    private void send404(RoutingContext ctx) {
        ctx.response().setStatusCode(404).end();
    }

    private void send204(RoutingContext ctx) {
        ctx.response().setStatusCode(204).end();
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        logger.error("Woops", err);
        ctx.response().setStatusCode(500).end();
    }

    private void sendBadRequest(RoutingContext ctx) {
        ctx.response().setStatusCode(400).end();
    }
}
