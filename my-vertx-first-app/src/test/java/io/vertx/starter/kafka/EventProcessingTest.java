package io.vertx.starter.kafka;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.kafka.admin.KafkaAdminClient;
import io.vertx.reactivex.kafka.client.consumer.KafkaConsumer;
import io.vertx.reactivex.kafka.client.producer.KafkaProducer;
import io.vertx.reactivex.kafka.client.producer.KafkaProducerRecord;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.starter.config.KafkaConfig;
import io.vertx.starter.config.PgConfig;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@DisplayName("Kafka event processing tests")
@Testcontainers
class EventProcessingTest {

  @ClassRule
  public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

  @ClassRule
  public static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:11.1")
          .withDatabaseName("test")
          .withUsername("postgres")
          .withPassword("password");

  private KafkaConsumer<String, JsonObject> consumer;
  private KafkaProducer<String, JsonObject> producer;

  @BeforeEach
  void resetPgAndKafka(Vertx vertx, VertxTestContext testContext) {
    consumer = KafkaConsumer.create(vertx, KafkaConfig.consumer("activity-service-test-" + System.currentTimeMillis()));
    producer = KafkaProducer.create(vertx, KafkaConfig.producer());
    KafkaAdminClient adminClient = KafkaAdminClient.create(vertx, KafkaConfig.producer());

    PgPool pgPool = PgPool.pool(vertx, PgConfig.pgConnectOpts(), new PoolOptions());
    pgPool.query("DELETE FROM testuser")
      .rxExecute()
      .flatMapCompletable(rs -> adminClient.rxDeleteTopics(Collections.singletonList("incoming.users")))
      .andThen(Completable.fromAction(pgPool::close))
      .onErrorComplete()
      .subscribe(
        testContext::completeNow,
        testContext::failNow);
  }

  @Test
  @DisplayName("Send events to create one user, and receive the event correctly")
  void observeUserCreationEvent(Vertx vertx, VertxTestContext testContext) {
    consumer.subscribe("incoming.users")
      .toFlowable()
      //.skip(1)
      .subscribe(record -> {
        JsonObject json = record.value();
        testContext.verify(() -> {
          assertThat(json.getString("userId")).isEqualTo("123");
          assertThat(json.getString("userName")).isEqualTo("jaime");
        });
        testContext.completeNow();
      }, testContext::failNow);

    vertx
      .rxDeployVerticle(new EventsVerticle())
      /*.flatMap(id -> {
        JsonObject steps = new JsonObject()
          .put("userId", "123")
          .put("userName", "jaime")
          .put("eventType", "CREATE");
        return producer.rxSend(KafkaProducerRecord.create("incoming.users", "123", steps));
      })*/
      .flatMap(id -> {
        JsonObject steps = new JsonObject()
          .put("userId", "123")
          .put("userName", "jaime");
        return producer.rxSend(KafkaProducerRecord.create("incoming.users", "123", steps));
      })
      .subscribe(ok -> {
      }, testContext::failNow);
  }
}
