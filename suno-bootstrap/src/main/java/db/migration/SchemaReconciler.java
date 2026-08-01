package db.migration;

import org.flywaydb.core.api.FlywayException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static db.migration.CanonicalSchemaManifest.ColumnSpec;
import static db.migration.CanonicalSchemaManifest.ForeignKeySpec;
import static db.migration.CanonicalSchemaManifest.IndexSpec;
import static db.migration.CanonicalSchemaManifest.TableSpec;
import static db.migration.CanonicalSchemaManifest.UniqueSpec;

final class SchemaReconciler {

    private final Connection connection;
    private final DatabaseDialect dialect;
    private final DatabaseMetaData metadata;
    private final String catalog;

    SchemaReconciler(Connection connection) throws SQLException {
        this.connection = connection;
        this.metadata = connection.getMetaData();
        this.catalog = connection.getCatalog();
        this.dialect = DatabaseDialect.fromProductName(metadata.getDatabaseProductName());
    }

    void reconcile(List<TableSpec> tables) throws SQLException {
        Set<String> existingTables = tableNames();
        for (TableSpec table : tables) {
            if (!existingTables.contains(table.name())) {
                throw operatorAction("Canonical table " + table.name()
                        + " is missing after its feature baseline; restore the baseline migration and retry");
            }
            reconcileColumns(table);
            reconcilePrimaryKey(table);
            reconcileUniques(table);
        }
        for (TableSpec table : tables) {
            reconcileForeignKeys(table);
            reconcileIndexes(table);
        }
    }

    private void reconcileColumns(TableSpec table) throws SQLException {
        Map<String, ColumnMetadata> existing = columns(table.name());
        for (ColumnSpec expected : table.columns()) {
            ColumnMetadata actual = existing.get(expected.name());
            if (actual == null) {
                addMissingColumn(table, expected);
            } else {
                validateColumn(table, expected, actual);
            }
        }
        for (String existingName : existing.keySet()) {
            boolean declared = table.columns().stream().anyMatch(column -> column.name().equals(existingName));
            if (!declared) {
                throw operatorAction("Unexpected column " + table.name() + "." + existingName
                        + " is outside the canonical manifest; review and resolve it before migration");
            }
        }
    }

    private void addMissingColumn(TableSpec table, ColumnSpec column) throws SQLException {
        long rows = rowCount(table.name());
        if (!column.nullable() && rows > 0 && column.backfillExpression() == null) {
            throw operatorAction("Cannot add required column " + table.name() + "." + column.name()
                    + " to a populated table because no approved backfill exists");
        }

        boolean primaryColumn = table.primaryKey().contains(column.name());
        boolean addNullableFirst = !column.nullable() && rows > 0;
        StringBuilder sql = new StringBuilder("ALTER TABLE ")
                .append(dialect.quote(table.name()))
                .append(" ADD COLUMN ")
                .append(dialect.quote(column.name()))
                .append(' ')
                .append(column.sqlType());
        if (column.autoIncrement()) {
            sql.append(" AUTO_INCREMENT");
        }
        if (column.defaultExpression() != null) {
            sql.append(" DEFAULT ").append(column.defaultExpression());
        }
        sql.append(addNullableFirst || column.nullable() ? " NULL" : " NOT NULL");
        if (primaryColumn && column.autoIncrement()) {
            sql.append(" PRIMARY KEY");
        }
        execute(sql.toString());

        if (addNullableFirst) {
            backfillAndRequireNotNull(table.name(), column);
        }
    }

    private void validateColumn(TableSpec table, ColumnSpec expected, ColumnMetadata actual) throws SQLException {
        String qualified = table.name() + "." + expected.name();
        boolean typeMatches = expected.acceptedJdbcTypes().contains(actual.jdbcType());
        boolean lengthMatches = expected.minimumLength() == 0 || actual.size() == expected.minimumLength();
        if ((!typeMatches || !lengthMatches) && reconcileKnownLegacyType(table, expected, actual)) {
            actual = columns(table.name()).get(expected.name());
            typeMatches = expected.acceptedJdbcTypes().contains(actual.jdbcType());
            lengthMatches = expected.minimumLength() == 0 || actual.size() == expected.minimumLength();
        }
        if (!typeMatches) {
            throw operatorAction("Column " + qualified + " has JDBC type " + actual.typeName()
                    + " but canonical type " + expected.sqlType() + " is required");
        }
        if (!lengthMatches) {
            throw operatorAction("Column " + qualified + " length " + actual.size()
                    + " differs from canonical length " + expected.minimumLength());
        }
        if (expected.precision() > 0
                && (actual.size() < expected.precision() || actual.scale() != expected.scale())) {
            throw operatorAction("Column " + qualified + " precision/scale "
                    + actual.size() + "/" + actual.scale() + " does not satisfy canonical "
                    + expected.precision() + "/" + expected.scale());
        }
        if (expected.autoIncrement() != actual.autoIncrement()) {
            throw operatorAction("Column " + qualified + " auto-increment setting differs from the manifest");
        }
        if (expected.nullable() && !actual.nullable()) {
            throw operatorAction("Column " + qualified
                    + " is unexpectedly NOT NULL; operator must decide whether loosening it is safe");
        }
        if (!expected.nullable() && actual.nullable()) {
            backfillAndRequireNotNull(table.name(), expected);
        }
    }

    private boolean reconcileKnownLegacyType(
            TableSpec table,
            ColumnSpec expected,
            ColumnMetadata actual
    ) throws SQLException {
        String qualified = table.name() + "." + expected.name();
        if (dialect == DatabaseDialect.H2
                && qualified.equals("suno_payment_replay_auto_handle_idempotency.response_json")
                && actual.jdbcType() == java.sql.Types.VARCHAR) {
            execute(dialect.changeTypeSql(table.name(), expected.name(), "CLOB", expected.nullable()));
            return true;
        }
        if (qualified.equals("suno_auth_export_task.content_text")
                && (actual.jdbcType() == java.sql.Types.CLOB
                || actual.jdbcType() == java.sql.Types.LONGVARCHAR
                || actual.jdbcType() == java.sql.Types.VARCHAR)
                && actual.size() != expected.minimumLength()) {
            long longestValue = maximumCharacterLength(table.name(), expected.name());
            if (longestValue > expected.minimumLength()) {
                throw operatorAction("Cannot narrow " + qualified + " to " + expected.sqlType()
                        + " because existing data reaches " + longestValue + " characters");
            }
            execute(dialect.changeTypeSql(
                    table.name(), expected.name(), expected.sqlType(), expected.nullable()));
            return true;
        }
        return false;
    }

    private void backfillAndRequireNotNull(String table, ColumnSpec column) throws SQLException {
        if (column.backfillExpression() == null) {
            throw operatorAction("Cannot make " + table + "." + column.name()
                    + " NOT NULL because no approved backfill exists");
        }
        execute("UPDATE " + dialect.quote(table) + " SET " + dialect.quote(column.name()) + " = "
                + quoteBackfillExpression(column.backfillExpression()) + " WHERE "
                + dialect.quote(column.name()) + " IS NULL");
        long remainingNulls = nullCount(table, column.name());
        if (remainingNulls != 0) {
            throw operatorAction("Backfill for " + table + "." + column.name() + " left "
                    + remainingNulls + " NULL row(s); correct the data and retry");
        }
        execute(dialect.setNotNullSql(table, column.name(), column.sqlType()));
    }

    private String quoteBackfillExpression(String expression) {
        if (expression.matches("[a-z][a-z0-9_]*")) {
            return dialect.quote(expression);
        }
        return expression;
    }

    private void reconcilePrimaryKey(TableSpec table) throws SQLException {
        List<SequencedColumn> primary = new ArrayList<>();
        try (ResultSet rows = metadata.getPrimaryKeys(catalog, null, table.name())) {
            while (rows.next()) {
                primary.add(new SequencedColumn(
                        rows.getShort("KEY_SEQ"), lower(rows.getString("COLUMN_NAME"))));
            }
        }
        List<String> actual = orderedColumns(primary);
        if (actual.isEmpty()) {
            execute("ALTER TABLE " + dialect.quote(table.name()) + " ADD CONSTRAINT "
                    + dialect.quote(primaryKeyName(table.name())) + " PRIMARY KEY ("
                    + quotedColumns(table.primaryKey()) + ")");
        } else if (!actual.equals(table.primaryKey())) {
            throw operatorAction("Primary key on " + table.name() + " uses " + actual
                    + " but canonical columns are " + table.primaryKey());
        }
    }

    private void reconcileUniques(TableSpec table) throws SQLException {
        List<IndexMetadata> indexes = indexes(table.name());
        for (UniqueSpec unique : table.uniques()) {
            IndexMetadata sameName = findByName(indexes, unique.name());
            if (sameName != null && (!sameName.unique() || !sameName.columns().equals(unique.columns()))) {
                throw operatorAction("Existing object " + unique.name() + " on " + table.name()
                        + " does not match canonical UNIQUE columns " + unique.columns());
            }
            boolean equivalent = indexes.stream().anyMatch(index ->
                    index.unique() && index.columns().equals(unique.columns()));
            if (!equivalent) {
                execute("ALTER TABLE " + dialect.quote(table.name()) + " ADD CONSTRAINT "
                        + dialect.quote(unique.name()) + " UNIQUE (" + quotedColumns(unique.columns()) + ")");
                indexes = indexes(table.name());
            }
        }
    }

    private void reconcileForeignKeys(TableSpec table) throws SQLException {
        List<ForeignKeyMetadata> existing = foreignKeys(table.name());
        for (ForeignKeySpec foreignKey : table.foreignKeys()) {
            ForeignKeyMetadata sameName = existing.stream()
                    .filter(key -> key.name().equalsIgnoreCase(foreignKey.name()))
                    .findFirst()
                    .orElse(null);
            if (sameName != null && !sameName.matches(foreignKey)) {
                throw operatorAction("Existing foreign key " + foreignKey.name() + " on " + table.name()
                        + " does not match canonical reference " + foreignKey.referencedTable());
            }
            boolean equivalent = existing.stream().anyMatch(key -> key.matches(foreignKey));
            if (!equivalent) {
                execute("ALTER TABLE " + dialect.quote(table.name()) + " ADD CONSTRAINT "
                        + dialect.quote(foreignKey.name()) + " FOREIGN KEY ("
                        + quotedColumns(foreignKey.columns()) + ") REFERENCES "
                        + dialect.quote(foreignKey.referencedTable()) + " ("
                        + quotedColumns(foreignKey.referencedColumns()) + ")");
                existing = foreignKeys(table.name());
            }
        }
    }

    private void reconcileIndexes(TableSpec table) throws SQLException {
        List<IndexMetadata> existing = indexes(table.name());
        for (IndexSpec index : table.indexes()) {
            IndexMetadata sameName = findByName(existing, index.name());
            if (sameName != null && (sameName.unique() || !sameName.columns().equals(index.columns()))) {
                throw operatorAction("Existing index " + index.name() + " on " + table.name()
                        + " does not match canonical non-unique columns " + index.columns());
            }
            boolean equivalent = existing.stream().anyMatch(actual ->
                    !actual.unique() && actual.columns().equals(index.columns()));
            if (!equivalent) {
                execute("CREATE INDEX " + dialect.quote(index.name()) + " ON "
                        + dialect.quote(table.name()) + " (" + quotedColumns(index.columns()) + ")");
                existing = indexes(table.name());
            }
        }
    }

    private Set<String> tableNames() throws SQLException {
        Set<String> names = new TreeSet<>();
        try (ResultSet rows = metadata.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rows.next()) {
                names.add(lower(rows.getString("TABLE_NAME")));
            }
        }
        return names;
    }

    private Map<String, ColumnMetadata> columns(String table) throws SQLException {
        Map<String, ColumnMetadata> columns = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getColumns(catalog, null, table, "%")) {
            while (rows.next()) {
                String name = lower(rows.getString("COLUMN_NAME"));
                columns.put(name, new ColumnMetadata(
                        rows.getInt("DATA_TYPE"), rows.getString("TYPE_NAME"),
                        rows.getInt("COLUMN_SIZE"), rows.getInt("DECIMAL_DIGITS"),
                        rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        "YES".equalsIgnoreCase(rows.getString("IS_AUTOINCREMENT"))));
            }
        }
        return columns;
    }

    private List<IndexMetadata> indexes(String table) throws SQLException {
        Map<String, MutableIndex> grouped = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getIndexInfo(catalog, null, table, false, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                if (name == null || column == null) {
                    continue;
                }
                grouped.computeIfAbsent(lower(name), ignored ->
                                new MutableIndex(name, !booleanValue(rows, "NON_UNIQUE")))
                        .add(rows.getShort("ORDINAL_POSITION"), lower(column));
            }
        }
        return grouped.values().stream().map(MutableIndex::immutable).toList();
    }

    private List<ForeignKeyMetadata> foreignKeys(String table) throws SQLException {
        Map<String, MutableForeignKey> grouped = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getImportedKeys(catalog, null, table)) {
            while (rows.next()) {
                String name = rows.getString("FK_NAME");
                if (name == null) {
                    name = "<unnamed:" + rows.getString("FKCOLUMN_NAME") + ">";
                }
                String key = lower(name);
                MutableForeignKey foreignKey = grouped.get(key);
                if (foreignKey == null) {
                    foreignKey = new MutableForeignKey(name, lower(rows.getString("PKTABLE_NAME")));
                    grouped.put(key, foreignKey);
                }
                foreignKey.add(rows.getShort("KEY_SEQ"), lower(rows.getString("FKCOLUMN_NAME")),
                        lower(rows.getString("PKCOLUMN_NAME")));
            }
        }
        return grouped.values().stream().map(MutableForeignKey::immutable).toList();
    }

    private long rowCount(String table) throws SQLException {
        return scalar("SELECT COUNT(*) FROM " + dialect.quote(table));
    }

    private long nullCount(String table, String column) throws SQLException {
        return scalar("SELECT COUNT(*) FROM " + dialect.quote(table) + " WHERE "
                + dialect.quote(column) + " IS NULL");
    }

    private long maximumCharacterLength(String table, String column) throws SQLException {
        return scalar("SELECT MAX(CHAR_LENGTH(" + dialect.quote(column) + ")) FROM "
                + dialect.quote(table));
    }

    private long scalar(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new FlywayException("Canonical schema reconciliation failed for SQL [" + sql
                    + "]; operator resolution required before retrying", exception);
        }
    }

    private String quotedColumns(List<String> columns) {
        return columns.stream().map(dialect::quote).reduce((left, right) -> left + ", " + right).orElseThrow();
    }

    private static String primaryKeyName(String table) {
        return "pk_" + table;
    }

    private static IndexMetadata findByName(List<IndexMetadata> indexes, String name) {
        return indexes.stream().filter(index -> index.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private static boolean booleanValue(ResultSet rows, String column) {
        try {
            return rows.getBoolean(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> orderedColumns(List<SequencedColumn> columns) {
        return columns.stream()
                .sorted(Comparator.comparingInt(SequencedColumn::sequence))
                .map(SequencedColumn::column)
                .toList();
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static FlywayException operatorAction(String message) {
        return new FlywayException(message + "; operator resolution required before retrying");
    }

    private record ColumnMetadata(
            int jdbcType,
            String typeName,
            int size,
            int scale,
            boolean nullable,
            boolean autoIncrement
    ) {
    }

    private record SequencedColumn(int sequence, String column) {
    }

    private record IndexMetadata(String name, boolean unique, List<String> columns) {
    }

    private record ForeignKeyMetadata(
            String name,
            List<String> columns,
            String referencedTable,
            List<String> referencedColumns
    ) {
        boolean matches(ForeignKeySpec expected) {
            return columns.equals(expected.columns())
                    && referencedTable.equals(expected.referencedTable())
                    && referencedColumns.equals(expected.referencedColumns());
        }
    }

    private static final class MutableIndex {
        private final String name;
        private final boolean unique;
        private final List<SequencedColumn> columns = new ArrayList<>();

        private MutableIndex(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }

        void add(int sequence, String column) {
            columns.add(new SequencedColumn(sequence, column));
        }

        IndexMetadata immutable() {
            return new IndexMetadata(name, unique, orderedColumns(columns));
        }
    }

    private static final class MutableForeignKey {
        private final String name;
        private final String referencedTable;
        private final List<SequencedColumn> columns = new ArrayList<>();
        private final List<SequencedColumn> referencedColumns = new ArrayList<>();

        private MutableForeignKey(String name, String referencedTable) {
            this.name = name;
            this.referencedTable = referencedTable;
        }

        void add(int sequence, String column, String referencedColumn) {
            columns.add(new SequencedColumn(sequence, column));
            referencedColumns.add(new SequencedColumn(sequence, referencedColumn));
        }

        ForeignKeyMetadata immutable() {
            return new ForeignKeyMetadata(
                    name, orderedColumns(columns), referencedTable, orderedColumns(referencedColumns));
        }
    }
}
