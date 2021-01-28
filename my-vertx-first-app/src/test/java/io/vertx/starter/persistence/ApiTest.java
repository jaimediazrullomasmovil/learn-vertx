package io.vertx.starter.persistence;

import io.reactivex.Completable;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Tuple;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.starter.config.PgConfig;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@DisplayName("HTTP API tests")
@Testcontainers
public class ApiTest {

  @ClassRule
  public static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:11.1")
          .withDatabaseName("test")
          .withUsername("postgres")
          .withPassword("password");

  private static RequestSpecification requestSpecification;
  private Vertx vertx;
  private VertxTestContext testContext;

  @BeforeAll
  static void prepareSpec() {
    requestSpecification = new RequestSpecBuilder()
      .addFilters(asList(new ResponseLoggingFilter(), new RequestLoggingFilter()))
      .setBaseUri("http://localhost:3001/")
      .build();
  }

  @BeforeEach
  void prepareDb(Vertx vertx, VertxTestContext testContext) {
    this.vertx = vertx;
    this.testContext = testContext;
    String insertQuery = "INSERT INTO testuser VALUES($1, $2)";
    List<Tuple> data = Arrays.asList(
      Tuple.of("1", "cristina"),
      Tuple.of("2", "ivan"),
      Tuple.of("3", "pablo"),
      Tuple.of("4", "jaime"),
      Tuple.of("5", "ruben"),
      Tuple.of("6", "sandra"),
      Tuple.of("7", "sara"),
      Tuple.of("8", "filomena"),
      Tuple.of("9", "fernando")
    );
    PgPool pgPool = PgPool.pool(vertx, PgConfig.pgConnectOpts(), new PoolOptions());

    pgPool.query("DELETE FROM testuser")
      .rxExecute()
      .flatMap(rows -> pgPool.preparedQuery(insertQuery).rxExecuteBatch(data))
      .ignoreElement()
      .andThen(vertx.rxDeployVerticle(new ApiVerticle()))
      .ignoreElement()
      .andThen(Completable.fromAction(pgPool::close))
      .subscribe(testContext::completeNow, testContext::failNow);
  }

  @Test
  @DisplayName("When get all users, then return HTTP 200 with users list")
  void getAllUsersInDatabase() {
    JsonPath jsonPath = given()
      .spec(requestSpecification)
      .accept(ContentType.JSON)
      .get("/users")
      .then()
      .assertThat()
      .statusCode(200)
      .extract()
            .jsonPath();

    assertThat(jsonPath.getList("$").size()).isEqualTo(9);
  }

  @Test
  @DisplayName("Given userId exists in db, When get one user, then return HTTP 200 with user info")
  void getOneUserInDatabase() {
    given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .get("/users/1")
            .then()
            .assertThat()
            .statusCode(200);

    given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .get("/users/8")
            .then()
            .assertThat()
            .statusCode(200);
  }

  @Test
  @DisplayName("Given userId not exists in db, When get one user, then HTTP 404 return")
  void check404() {
    given()
      .spec(requestSpecification)
      .accept(ContentType.JSON)
      .get("/users/10")
      .then()
      .assertThat()
      .statusCode(404);

    given()
      .spec(requestSpecification)
      .accept(ContentType.JSON)
      .get("/users/11")
      .then()
      .assertThat()
      .statusCode(404);
  }

  @Test
  @DisplayName("When create one user, then return HTTP 202 with user info")
  void createNewUser(){
    JsonObject body = new JsonObject();
    body.put("userName", "new user");

    JsonPath jsonPath = given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .body(body.toString())
            .post("/users")
            .then()
            .assertThat()
            .statusCode(202)
            .extract()
            .jsonPath();
    assertThat(jsonPath.getString("userName")).isEqualTo("new user");
    assertThat(isValidUUID(jsonPath.getString("userId"))).isTrue();
  }

  @Test
  @DisplayName("Given userId exists in db, When update one user, then return HTTP 200 with user info")
  void updateUser(){
    JsonObject body = new JsonObject();
    body.put("userName", "update user");

    JsonPath jsonPath = given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .body(body.toString())
            .put("/users/8")
            .then()
            .assertThat()
            .statusCode(200)
            .extract()
            .jsonPath();
    assertThat(jsonPath.getString("userName")).isEqualTo("update user");
    assertThat(jsonPath.getString("userId")).isEqualTo("8");
  }

  @Test
  @DisplayName("Given empty body, When update one user, then return HTTP 400")
  void check400InUpdateOperation() {
    given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .put("/users/11")
            .then()
            .assertThat()
            .statusCode(400);

    given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .put("/users/0")
            .then()
            .assertThat()
            .statusCode(400);
  }

  @Test
  @DisplayName("Given userId not exists in db, When update one user, then return HTTP 404")
  void check404InUpdateOperation() {
    JsonObject body = new JsonObject();
    body.put("userName", "update user");
    given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .body(body.toString())
            .put("/users/11")
            .then()
            .assertThat()
            .statusCode(404);

    given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .body(body.toString())
            .put("/users/0")
            .then()
            .assertThat()
            .statusCode(404);
  }

  @Test
  @DisplayName("Given userId not exists in db, When delete one user, then return HTTP 202")
  void deleteUser(){
    JsonPath jsonPath = given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .delete("/users/8")
            .then()
            .assertThat()
            .statusCode(202) //async response
            .extract()
            .jsonPath();
    //empty json
  }

  @Test
  @DisplayName("When delete one user, check for HTTP 404 when the userId not exist in database")
  void check204InDeleteOperation() {
    given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .delete("/users/11")
            .then()
            .assertThat()
            .statusCode(204);

    given()
            .spec(requestSpecification)
            .accept(ContentType.JSON)
            .delete("/users/0")
            .then()
            .assertThat()
            .statusCode(204);
  }

  private boolean isValidUUID(String s){
    try{
      UUID.fromString(s);
      return true;
    } catch (IllegalArgumentException exception){
      return false;
    }
  }
}
