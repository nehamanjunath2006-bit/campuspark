package database;

import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL
            = "jdbc:postgresql://localhost:5432/campuspark";

    private static final String USER
            = "postgres";

    private DBConnection() {
    }

    public static Connection getConnection()
            throws SQLException {

        String password = System.getenv("CAMPUSPARK_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            Console console = System.console();
            if (console != null) {
                char[] entered = console.readPassword("PostgreSQL password for postgres: ");
                password = entered == null ? "" : new String(entered);
            } else {
                throw new SQLException("Database password is not set. Set CAMPUSPARK_DB_PASSWORD and retry.");
            }
        }

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC driver is missing from the runtime classpath.", e);
        }

        try {
            return DriverManager.getConnection(URL, USER, password);
        } catch (SQLException e) {
            String detail = switch (e.getSQLState()) {
                case "3D000" ->
                    "Database 'campuspark' does not exist.";
                case "28P01" ->
                    "The PostgreSQL password is incorrect.";
                case "08001", "08006" ->
                    "PostgreSQL is not running or is not reachable on localhost:5432.";
                default ->
                    "Check that PostgreSQL is running, the campuspark database exists, and the password is correct.";
            };
            throw new SQLException("Cannot connect to CampusPark PostgreSQL database. " + detail, e);
        }
    }
}
