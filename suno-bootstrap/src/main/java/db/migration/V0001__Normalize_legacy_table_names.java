package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class V0001__Normalize_legacy_table_names extends BaseJavaMigration {

    private static final Map<String, String> LEGACY_TO_CANONICAL = legacyTableNames();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseDialect dialect = DatabaseDialect.fromProductName(
                connection.getMetaData().getDatabaseProductName());
        Set<String> tableNames = readTableNames(connection);

        for (Map.Entry<String, String> entry : LEGACY_TO_CANONICAL.entrySet()) {
            String legacy = entry.getKey();
            String canonical = entry.getValue();
            boolean legacyExists = tableNames.contains(legacy);
            boolean canonicalExists = tableNames.contains(canonical);

            if (legacyExists && canonicalExists) {
                long legacyRows = countRows(connection, dialect, legacy);
                if (legacyRows > 0) {
                    throw collision(legacy, canonical, legacyRows);
                }
                continue;
            }
            if (legacyExists) {
                execute(connection, dialect.renameTableSql(legacy, canonical));
                tableNames.remove(legacy);
                tableNames.add(canonical);
            }
        }
    }

    static Map<String, String> legacyTableNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (String legacy : new String[]{
                "user_account", "auth_refresh_token", "auth_token_blacklist", "auth_export_task",
                "product", "valuation_rule", "recycle_order", "logistics_track", "points_ledger",
                "resale_listing", "resale_favorite", "resale_order", "resale_review",
                "resale_review_vote", "resale_review_report", "operation_audit_log",
                "payment_idempotency", "payment_replay_auto_handle_idempotency", "payment_nonce",
                "payment_callback_log", "payment_replay_task"
        }) {
            names.put(legacy, "suno_" + legacy);
        }
        return Map.copyOf(names);
    }

    private static Set<String> readTableNames(Connection connection) throws SQLException {
        Set<String> names = new TreeSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(
                connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static long countRows(Connection connection, DatabaseDialect dialect, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + dialect.quote(table))) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static FlywayException collision(String legacy, String canonical, long legacyRows) {
        return new FlywayException("Legacy/canonical table collision: " + legacy + " and " + canonical
                + " both exist, and " + legacy + " contains " + legacyRows
                + " row(s); operator resolution required before migration. "
                + "No rows were copied, dropped, truncated, merged, or overwritten.");
    }
}
