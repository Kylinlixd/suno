package com.suno.mall.persistence;

import com.suno.mall.testsupport.MySqlContainerSupport;
import db.migration.CanonicalSchemaManifestExpectations;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaInvariantIT {

    @ParameterizedTest
    @EnumSource(DatabaseBackend.class)
    void cleanAndPopulatedLegacyDatabasesConvergeToTheManifestSchema(DatabaseBackend backend)
            throws Exception {
        if (backend == DatabaseBackend.MYSQL) {
            Assumptions.assumeTrue(MySqlContainerSupport.isDockerAvailable(),
                    MySqlContainerSupport.dockerUnavailableEvidence());
        }

        Database clean = backend.database("clean");
        migrate(clean);
        SchemaSignature cleanSchema = inspectCanonicalSchema(clean, backend);

        Database legacy = backend.database("legacy");
        executeLegacyFixture(legacy);
        migrate(legacy);
        SchemaSignature legacySchema = inspectCanonicalSchema(legacy, backend);

        assertThat(legacySchema).isEqualTo(cleanSchema);
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

    private static void executeLegacyFixture(Database database) throws SQLException {
        try (Connection connection = database.connect()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/legacy/legacy-schema.sql"));
        }
    }

    private static SchemaSignature inspectCanonicalSchema(Database database, DatabaseBackend backend)
            throws SQLException, ClassNotFoundException {
        Set<String> mappedTables = FlywayH2MigrationTest.mappedTableNames();
        Set<String> tables = tableNames(database);
        assertThat(tables).containsAll(mappedTables);
        CanonicalSchemaManifestExpectations.ExpectedSchema expected =
                CanonicalSchemaManifestExpectations.expectedSchema();
        assertThat(tables).containsAll(expected.tables());

        SchemaSignature signature = backend == DatabaseBackend.MYSQL
                ? mysqlInformationSchemaSignature(database)
                : h2MetadataSignature(database, mappedTables);
        assertThat(signature.columns()).containsAll(expected.columns());
        assertThat(signature.uniqueKeys()).containsAll(expected.uniqueKeys());
        assertThat(signature.foreignKeys()).containsAll(expected.foreignKeys());
        assertThat(signature.indexes()).containsAll(expected.indexes());
        return signature;
    }

    private static Set<String> tableNames(Database database) throws SQLException {
        Set<String> tables = new TreeSet<>();
        try (Connection connection = database.connect(); ResultSet rows = connection.getMetaData().getTables(
                connection.getCatalog(), null, "suno_%", new String[]{"TABLE"})) {
            while (rows.next()) {
                tables.add(rows.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tables;
    }

    private static SchemaSignature mysqlInformationSchemaSignature(Database database) throws SQLException {
        Set<String> columns = new TreeSet<>();
        Set<String> uniqueKeys = new TreeSet<>();
        Set<String> foreignKeys = new TreeSet<>();
        Set<String> indexes = new TreeSet<>();
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("""
                    SELECT table_name, column_name
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name LIKE 'suno\\_%' ESCAPE '\\\\'
                    """)) {
                while (rows.next()) {
                    columns.add(rows.getString(1) + "." + rows.getString(2));
                }
            }
            Map<String, TreeSet<String>> uniqueColumns = new LinkedHashMap<>();
            try (ResultSet rows = statement.executeQuery("""
                    SELECT kcu.table_name, kcu.constraint_name, kcu.column_name, kcu.ordinal_position
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                      ON tc.constraint_schema = kcu.constraint_schema
                     AND tc.table_name = kcu.table_name
                     AND tc.constraint_name = kcu.constraint_name
                    WHERE tc.constraint_schema = DATABASE()
                      AND tc.constraint_type = 'UNIQUE'
                      AND tc.table_name LIKE 'suno\\_%' ESCAPE '\\\\'
                    ORDER BY kcu.table_name, kcu.constraint_name, kcu.ordinal_position
                    """)) {
                while (rows.next()) {
                    uniqueColumns.computeIfAbsent(rows.getString(1) + "." + rows.getString(2),
                                    ignored -> new TreeSet<>())
                            .add(String.format("%05d:%s", rows.getInt(4), rows.getString(3)));
                }
            }
            uniqueColumns.forEach((key, value) -> uniqueKeys.add(key.substring(0, key.indexOf('.'))
                    + "." + columnList(value)));
            try (ResultSet rows = statement.executeQuery("""
                    SELECT table_name, column_name, referenced_table_name, referenced_column_name
                    FROM information_schema.key_column_usage
                    WHERE constraint_schema = DATABASE()
                      AND referenced_table_name IS NOT NULL
                      AND table_name LIKE 'suno\\_%' ESCAPE '\\\\'
                    """)) {
                while (rows.next()) {
                    foreignKeys.add(rows.getString(1) + "." + rows.getString(2)
                            + "->" + rows.getString(3) + "." + rows.getString(4));
                }
            }
            Map<String, TreeSet<String>> indexColumns = new LinkedHashMap<>();
            Map<String, Boolean> indexUnique = new LinkedHashMap<>();
            try (ResultSet rows = statement.executeQuery("""
                    SELECT table_name, index_name, column_name, seq_in_index, non_unique
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name LIKE 'suno\\_%' ESCAPE '\\\\'
                    ORDER BY table_name, index_name, seq_in_index
                    """)) {
                while (rows.next()) {
                    String key = rows.getString(1) + "." + rows.getString(2);
                    indexColumns.computeIfAbsent(key, ignored -> new TreeSet<>())
                            .add(String.format("%05d:%s", rows.getInt(4), rows.getString(3)));
                    indexUnique.put(key, !rows.getBoolean(5));
                }
            }
            indexColumns.forEach((key, value) -> indexes.add(key.substring(0, key.indexOf('.'))
                    + "." + indexUnique.get(key) + "." + columnList(value)));
        }
        return new SchemaSignature(columns, uniqueKeys, foreignKeys, indexes);
    }

    private static SchemaSignature h2MetadataSignature(Database database, Set<String> tables) throws SQLException {
        Set<String> columns = new TreeSet<>();
        Set<String> uniqueKeys = new TreeSet<>();
        Set<String> foreignKeys = new TreeSet<>();
        Set<String> indexes = new TreeSet<>();
        try (Connection connection = database.connect()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String table : tables) {
                try (ResultSet rows = metadata.getColumns(connection.getCatalog(), null, table, "%")) {
                    while (rows.next()) {
                        columns.add(table + "." + rows.getString("COLUMN_NAME").toLowerCase());
                    }
                }
                try (ResultSet rows = metadata.getImportedKeys(connection.getCatalog(), null, table)) {
                    while (rows.next()) {
                        foreignKeys.add(table + "." + rows.getString("FKCOLUMN_NAME").toLowerCase() + "->"
                                + rows.getString("PKTABLE_NAME").toLowerCase() + "."
                                + rows.getString("PKCOLUMN_NAME").toLowerCase());
                    }
                }
                Map<String, TreeSet<String>> indexColumns = new LinkedHashMap<>();
                Map<String, Boolean> indexUnique = new LinkedHashMap<>();
                try (ResultSet rows = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
                    while (rows.next()) {
                        String indexName = rows.getString("INDEX_NAME");
                        String column = rows.getString("COLUMN_NAME");
                        if (indexName == null || column == null) {
                            continue;
                        }
                        String key = indexName.toLowerCase();
                        indexColumns.computeIfAbsent(key, ignored -> new TreeSet<>()).add(
                                String.format("%05d:%s", rows.getShort("ORDINAL_POSITION"), column.toLowerCase()));
                        indexUnique.put(key, !rows.getBoolean("NON_UNIQUE"));
                    }
                }
                indexColumns.forEach((key, value) -> {
                    String signature = table + "." + indexUnique.get(key) + "." + columnList(value);
                    indexes.add(signature);
                    if (indexUnique.get(key)) {
                        uniqueKeys.add(table + "." + columnList(value));
                    }
                });
            }
        }
        return new SchemaSignature(columns, uniqueKeys, foreignKeys, indexes);
    }

    private static String columnList(Set<String> orderedColumns) {
        return orderedColumns.stream().map(value -> value.substring(value.indexOf(':') + 1))
                .reduce((left, right) -> left + "," + right).orElseThrow();
    }

    private enum DatabaseBackend {
        H2 {
            @Override
            Database database(String label) {
                return new Database(FlywayH2MigrationTest.h2Url("schema-" + label), "sa", "");
            }
        },
        MYSQL {
            @Override
            Database database(String label) throws SQLException {
                return new Database(MySqlContainerSupport.createDatabaseUrl("schema-" + label),
                        MySqlContainerSupport.mysql().getUsername(), MySqlContainerSupport.mysql().getPassword());
            }
        };

        abstract Database database(String label) throws SQLException;
    }

    private record Database(String url, String username, String password) {
        Connection connect() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
    }

    private record SchemaSignature(
            Set<String> columns,
            Set<String> uniqueKeys,
            Set<String> foreignKeys,
            Set<String> indexes
    ) {
    }
}
