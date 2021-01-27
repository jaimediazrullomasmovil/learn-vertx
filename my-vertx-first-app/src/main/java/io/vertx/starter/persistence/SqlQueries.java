package io.vertx.starter.persistence;

public interface SqlQueries {

    static String insertStepEvent() {
        return "INSERT INTO stepevent VALUES($1, $2, current_timestamp, $3)";
    }

    static String insertUser() {
        return "INSERT INTO testuser (id, name) VALUES ($1, $2)";
    }
    static String updateUser() {
        return "UPDATE testuser SET (id, name) = ($1, $2) " +
                "  WHERE id= $1";
    }
    static String deleteUser() {
        return "DELETE FROM testuser WHERE id = $1";
    }

    static String totalUsers() {
        return "SELECT * FROM testuser";
    }
    static String getOneById() {
        return "SELECT * FROM testuser WHERE id= $1";
    }
}
