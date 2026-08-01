package com.suno.mall.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import com.suno.mall.testsupport.MySqlContainerSupport;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacySchemaCompatibilityIT {

    private static final List<String> LEGACY_TABLES = List.of(
            "user_account", "auth_refresh_token", "auth_token_blacklist", "auth_export_task",
            "product", "valuation_rule", "recycle_order", "logistics_track", "points_ledger",
            "resale_listing", "resale_favorite", "resale_order", "resale_review",
            "resale_review_vote", "resale_review_report", "operation_audit_log",
            "payment_idempotency", "payment_replay_auto_handle_idempotency", "payment_nonce",
            "payment_callback_log", "payment_replay_task"
    );

    @Test
    void migratesPopulatedLegacyH2WithoutLosingDataAndConvergesWithAnEmptyDatabase() throws Exception {
        Database legacy = Database.h2("legacy");
        executeLegacyFixture(legacy);

        migrate(legacy);

        assertPopulatedLegacyData(legacy);
        Database clean = Database.h2("clean-signature");
        migrate(clean);
        assertThat(schemaSignature(legacy)).isEqualTo(schemaSignature(clean));
    }

    @Test
    void rejectsPopulatedLegacyAndCanonicalTableCollisionWithOperatorAction() throws Exception {
        Database database = Database.h2("collision");
        assertCollisionIsRejectedWithoutChangingEitherTable(database);
    }

    @Test
    void rejectsPopulatedLegacyAndCanonicalTableCollisionOnMysqlWithOperatorAction() throws Exception {
        Assumptions.assumeTrue(MySqlContainerSupport.isDockerAvailable(),
                MySqlContainerSupport.dockerUnavailableEvidence());
        Database database = Database.mysql("collision");
        assertCollisionIsRejectedWithoutChangingEitherTable(database);
    }

    @Test
    void refusesToNarrowOversizedLegacyContentInsteadOfTruncatingIt() throws Exception {
        Database database = Database.h2("oversized-content");
        executeLegacyFixture(database);
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE auth_export_task SET content_text = REPEAT('x', 300)");
        }

        assertThatThrownBy(() -> migrate(database))
                .hasStackTraceContaining("suno_auth_export_task.content_text")
                .hasStackTraceContaining("300")
                .hasStackTraceContaining("operator resolution required");
        try (Connection connection = database.connect(); Statement statement = connection.createStatement();
             ResultSet value = statement.executeQuery(
                     "SELECT CHAR_LENGTH(content_text) FROM suno_auth_export_task WHERE id = 1")) {
            assertThat(value.next()).isTrue();
            assertThat(value.getInt(1)).isEqualTo(300);
        }
    }

    @Test
    void migratesPopulatedLegacyMysqlWhenDockerIsAvailable() throws Exception {
        Assumptions.assumeTrue(MySqlContainerSupport.isDockerAvailable(),
                MySqlContainerSupport.dockerUnavailableEvidence());
        Database database = new Database(MySqlContainerSupport.createDatabaseUrl("legacy"),
                MySqlContainerSupport.mysql().getUsername(), MySqlContainerSupport.mysql().getPassword());
        executeLegacyFixture(database);

        migrate(database);

        assertPopulatedLegacyData(database);
    }

    private static void executeLegacyFixture(Database database) throws Exception {
        try (Connection connection = database.connect()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/legacy/legacy-schema.sql"));
        }
    }

    private static void migrate(Database database) {
        Flyway.configure()
                .dataSource(database.url(), database.username(), database.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    private static void assertCollisionIsRejectedWithoutChangingEitherTable(Database database) throws Exception {
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE user_account (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE suno_user_account (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO user_account VALUES (1)");
            statement.execute("INSERT INTO suno_user_account VALUES (2)");
        }

        assertThatThrownBy(() -> migrate(database))
                .hasStackTraceContaining("user_account")
                .hasStackTraceContaining("suno_user_account")
                .hasStackTraceContaining("operator resolution required");
        assertThat(rowCount(database, "user_account")).isOne();
        assertThat(rowCount(database, "suno_user_account")).isOne();
    }

    private static void assertPopulatedLegacyData(Database database) throws Exception {
        Set<String> tables = tableNames(database);
        for (String legacyTable : LEGACY_TABLES) {
            assertThat(tables).doesNotContain(legacyTable).contains("suno_" + legacyTable);
            assertThat(rowCount(database, "suno_" + legacyTable))
                    .as("preserved row count for %s", legacyTable)
                    .isOne();
        }
        assertThat(tables).containsAll(FlywayH2MigrationTest.mappedTableNames());

        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            try (ResultSet user = statement.executeQuery(
                    "SELECT id, username, points FROM suno_user_account WHERE id = 1001")) {
                assertThat(user.next()).isTrue();
                assertThat(user.getString("username")).isEqualTo("legacy-user");
                assertThat(user.getInt("points")).isEqualTo(321);
            }
            try (ResultSet listing = statement.executeQuery(
                    "SELECT recycle_order_id, product_id, sale_price, created_at, updated_at "
                            + "FROM suno_resale_listing WHERE id = 1")) {
                assertThat(listing.next()).isTrue();
                assertThat(listing.getLong("recycle_order_id")).isOne();
                assertThat(listing.getLong("product_id")).isOne();
                assertThat(listing.getBigDecimal("sale_price")).isEqualByComparingTo("1500.00");
                assertThat(listing.getTimestamp("updated_at"))
                        .isEqualTo(listing.getTimestamp("created_at"));
            }
        }
        assertThat(hasIndex(database, "suno_recycle_order", "idx_recycle_order_status_created_at"))
                .as("renamed table retains its useful legacy index")
                .isTrue();
    }

    private static long rowCount(Database database, String table) throws Exception {
        try (Connection connection = database.connect(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static Set<String> tableNames(Database database) throws Exception {
        Set<String> names = new TreeSet<>();
        try (Connection connection = database.connect()) {
            try (ResultSet tables = connection.getMetaData().getTables(
                    connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }

    private static boolean hasIndex(Database database, String table, String indexName) throws Exception {
        try (Connection connection = database.connect(); ResultSet indexes = connection.getMetaData()
                .getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> schemaSignature(Database database) throws Exception {
        Set<String> signature = new TreeSet<>();
        Set<String> mappedTables = FlywayH2MigrationTest.mappedTableNames();
        try (Connection connection = database.connect()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String table : mappedTables) {
                try (ResultSet columns = metadata.getColumns(catalog, null, table, "%")) {
                    while (columns.next()) {
                        signature.add("column|" + table + "|"
                                + columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT) + "|"
                                + typeFamily(columns.getInt("DATA_TYPE")) + "|"
                                + columns.getInt("COLUMN_SIZE") + "|" + columns.getInt("DECIMAL_DIGITS") + "|"
                                + columns.getInt("NULLABLE"));
                    }
                }
                try (ResultSet keys = metadata.getPrimaryKeys(catalog, null, table)) {
                    while (keys.next()) {
                        signature.add("primary|" + table + "|" + keys.getShort("KEY_SEQ") + "|"
                                + keys.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                try (ResultSet keys = metadata.getImportedKeys(catalog, null, table)) {
                    while (keys.next()) {
                        signature.add("foreign|" + table + "|"
                                + keys.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT) + "|"
                                + keys.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT) + "|"
                                + keys.getString("PKCOLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                Map<String, IndexSignature> indexes = new LinkedHashMap<>();
                try (ResultSet rows = metadata.getIndexInfo(catalog, null, table, false, false)) {
                    while (rows.next()) {
                        String name = rows.getString("INDEX_NAME");
                        String column = rows.getString("COLUMN_NAME");
                        if (name == null || column == null) {
                            continue;
                        }
                        indexes.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored ->
                                        new IndexSignature(!rowsBoolean(rows, "NON_UNIQUE")))
                                .add(rows.getShort("ORDINAL_POSITION"), column);
                    }
                }
                for (IndexSignature index : indexes.values()) {
                    signature.add("index|" + table + "|" + index.unique + "|" + index.columns());
                }
            }
        }
        return signature;
    }

    private static boolean rowsBoolean(ResultSet rows, String column) {
        try {
            return rows.getBoolean(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String typeFamily(int jdbcType) {
        return switch (jdbcType) {
            case java.sql.Types.BIT, java.sql.Types.BOOLEAN, java.sql.Types.TINYINT -> "boolean/tinyint";
            case java.sql.Types.VARCHAR, java.sql.Types.LONGVARCHAR, java.sql.Types.CLOB -> "string/text";
            case java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> "decimal";
            case java.sql.Types.TIMESTAMP, java.sql.Types.TIMESTAMP_WITH_TIMEZONE -> "timestamp";
            default -> Integer.toString(jdbcType);
        };
    }

    private record Database(String url, String username, String password) {
        static Database h2(String label) {
            return new Database(FlywayH2MigrationTest.h2Url(label), "sa", "");
        }

        static Database mysql(String label) throws SQLException {
            return new Database(MySqlContainerSupport.createDatabaseUrl(label),
                    MySqlContainerSupport.mysql().getUsername(), MySqlContainerSupport.mysql().getPassword());
        }

        Connection connect() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
    }

    private static final class IndexSignature {
        private final boolean unique;
        private final List<String> columns = new ArrayList<>();

        private IndexSignature(boolean unique) {
            this.unique = unique;
        }

        void add(int ordinal, String column) {
            while (columns.size() < ordinal) {
                columns.add("");
            }
            columns.set(ordinal - 1, column.toLowerCase(Locale.ROOT));
        }

        String columns() {
            return String.join(",", columns);
        }
    }
}
