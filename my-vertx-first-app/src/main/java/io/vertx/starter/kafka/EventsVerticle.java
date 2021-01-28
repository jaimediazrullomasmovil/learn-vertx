package io.vertx.starter.kafka;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgException;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumer;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Tuple;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.starter.config.KafkaConfig;
import io.vertx.starter.config.PgConfig;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static io.vertx.starter.persistence.SqlQueries.*;

public class EventsVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(EventsVerticle.class);

  private KafkaConsumer<String, JsonObject> eventConsumer;
  private PgPool pgPool;

  @Override
  public Completable rxStart() {
    eventConsumer = KafkaConsumer.create(vertx, KafkaConfig.consumer("activity-service"));
    pgPool = PgPool.pool(vertx, PgConfig.pgConnectOpts(), new PoolOptions());

    eventConsumer
      .subscribe("incoming.users")
      .toFlowable()
      .flatMap(this::processRecord)
      .doOnError(err -> logger.error("Woops", err))
      .flatMap(this::commitKafkaConsumerOffset)
      .doOnError(err -> logger.error("Woops", err))
      .retryWhen(this::retryLater)
      .subscribe();

    return Completable.complete();
  }

  private Flowable<Throwable> retryLater(Flowable<Throwable> errs) {
    return errs.delay(10, TimeUnit.SECONDS, RxHelper.scheduler(vertx));
  }

  private Flowable<KafkaConsumerRecord<String, JsonObject>> processRecord(KafkaConsumerRecord<String, JsonObject> record) {
    JsonObject data = record.value();

    Tuple values = Tuple.of(data.getString("userId"));

    EventType eventType = EventType.valueOf(data.getString("eventType"));
    String query= "";
    switch (eventType) {
      case CREATE:
        query = insertUser();
        values.addString(data.getString("userName"));
        break;
      case UPDATE:
        query = updateUser();
        values.addString(data.getString("userName"));
        break;
      case DELETE:
        query = deleteUser();
        break;
      default:
        //error
        break;

    }
    return pgPool
      .preparedQuery(query)
      .rxExecute(values)
      .map(rs -> record)
      .onErrorReturn(err -> {
        if (duplicateKeyInsert(err)) {
          return record;
        } else {
          throw new RuntimeException(err);
        }
      })
      .toFlowable();
  }

  private boolean duplicateKeyInsert(Throwable err) {
    return (err instanceof PgException) && "23505".equals(((PgException) err).getCode());
  }

  private Publisher<?> commitKafkaConsumerOffset(KafkaConsumerRecord<String, JsonObject> record) {
    return eventConsumer.rxCommit().toFlowable();
  }
}
