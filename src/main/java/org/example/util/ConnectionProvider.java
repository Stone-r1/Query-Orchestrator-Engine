package org.example.util;

import org.example.data.PathConstants;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/*
 * Classic POM approach: no caching, no pooling.
 * Every call to get() opens a brand-new physical connection to the database.
 */
public class ConnectionProvider {

    private static final String URL;
    private static final String USERNAME;
    private static final String PASSWORD;

    static {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(PathConstants.JDBC_PROPERTIES)) {
            props.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to load queryRunner.properties: " + e.getMessage());
        }
        URL = props.getProperty("jdbc.url");
        USERNAME = props.getProperty("jdbc.username");
        PASSWORD = props.getProperty("jdbc.password");
    }

    /*
     * Opens and returns a new physical JDBC connection.
     * Caller is responsible for closing it (use try-with-resources).
     */
    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }
}
