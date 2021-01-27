package io.vertx.starter.config;


import io.vertx.pgclient.PgConnectOptions;

public class PgConfig {
    public static final int PORT = 5432;
    public static final String HOST = "0.0.0.0";
    public static final String DATABASE = "test";
    public static final String USER = "postgres";
    public static final String PASSWORD = "password";

    public static PgConnectOptions pgConnectOpts() {
        return new PgConnectOptions()
                .setPort(PORT)
                .setHost(HOST)
                .setDatabase(DATABASE)
                .setUser(USER)
                .setPassword(PASSWORD);
    }
}
