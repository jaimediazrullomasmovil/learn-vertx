import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  java
  application
  id("com.github.johnrengelman.shadow") version "5.2.0"
}

repositories {
  mavenCentral()
}

val vertxVersion = "4.0.0"
val junitVersion = "5.3.2"

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-core")
  implementation("io.vertx:vertx-mongo-client:$vertxVersion")
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-rx-java2:$vertxVersion")
  implementation("io.reactivex.rxjava2:rxjava:2.2.20")
  implementation("io.vertx:vertx-pg-client:$vertxVersion")
  implementation("ch.qos.logback:logback-classic:1.2.3")
  implementation("io.vertx:vertx-kafka-client")
  testImplementation("io.vertx:vertx-junit5-rx-java2")
  testImplementation("io.vertx:vertx-web-client:$vertxVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.testcontainers:junit-jupiter:1.15.1")
  testImplementation("org.assertj:assertj-core:3.11.1")
  testImplementation("org.testcontainers:postgresql:1.15.1")
  testImplementation("org.testcontainers:kafka:1.15.1")
  testImplementation("io.rest-assured:rest-assured:4.3.3")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

}

java {
  sourceCompatibility = JavaVersion.VERSION_11
}

application {
  mainClassName = "io.vertx.core.Launcher"
}

val mainVerticleName = "io.vertx.starter.MainVerticle"
val watchForChange = "src/**/*.java"
val doOnChange = "${projectDir}/gradlew classes"

tasks {
  test {
    useJUnitPlatform()
  }

  getByName<JavaExec>("run") {
    args = listOf("run", mainVerticleName, "--redeploy=${watchForChange}", "--launcher-class=${application.mainClassName}", "--on-redeploy=${doOnChange}")
  }

  withType<ShadowJar> {
    classifier = "fat"
    manifest {
      attributes["Main-Verticle"] = mainVerticleName
    }
    mergeServiceFiles()
  }
}
