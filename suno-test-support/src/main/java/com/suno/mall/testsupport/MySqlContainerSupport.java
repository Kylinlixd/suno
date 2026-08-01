package com.suno.mall.testsupport;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

/** Shared MySQL 8.4 fixture for Docker-backed integration tests. */
public interface MySqlContainerSupport {

    MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("suno")
            .withUsername("suno")
            .withPassword("suno-test")
            .withCommand("--default-time-zone=+00:00", "--character-set-server=utf8mb4")
            .withReuse(reuseRequested());

    static MySQLContainer<?> mysql() {
        return MYSQL;
    }

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static String dockerUnavailableEvidence() {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return "DockerClientFactory reported Docker available";
            }
            return "Docker unavailable: DockerClientFactory.isDockerAvailable() returned false"
                    + " (DOCKER_HOST=" + System.getenv("DOCKER_HOST") + ")";
        } catch (RuntimeException exception) {
            return "Docker unavailable: " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage();
        }
    }

    static String createDatabaseUrl(String label) throws SQLException {
        MYSQL.start();
        String database = "suno_it_" + label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_")
                + "_" + UUID.randomUUID().toString().replace("-", "");
        String databaseIdentifier = quoteIdentifier(database);
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseIdentifier + " CHARACTER SET utf8mb4");
            statement.execute("GRANT ALL PRIVILEGES ON " + databaseIdentifier + ".* TO "
                    + quoteLiteral(MYSQL.getUsername()) + "@'%'");
        }
        String jdbcUrl = MYSQL.getJdbcUrl();
        int queryStart = jdbcUrl.indexOf('?');
        int databaseEnd = queryStart < 0 ? jdbcUrl.length() : queryStart;
        int databaseStart = jdbcUrl.lastIndexOf('/', databaseEnd - 1);
        return jdbcUrl.substring(0, databaseStart + 1) + database + jdbcUrl.substring(databaseEnd);
    }

    private static boolean reuseRequested() {
        return !isCi()
                && Boolean.getBoolean("suno.testcontainers.reuse")
                && Boolean.getBoolean("testcontainers.reuse.enable");
    }

    private static boolean isCi() {
        return System.getenv("CI") != null;
    }

    private static String quoteIdentifier(String identifier) {
        if (!identifier.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Unsafe MySQL database identifier: " + identifier);
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    private static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
