package db.migration;

import java.util.Locale;

enum DatabaseDialect {
    H2("\"") {
        @Override
        String renameTableSql(String oldName, String newName) {
            return "ALTER TABLE " + quote(oldName) + " RENAME TO " + quote(newName);
        }

        @Override
        String setNotNullSql(String table, String column, String columnType) {
            return "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(column) + " SET NOT NULL";
        }

        @Override
        String changeTypeSql(String table, String column, String columnType, boolean nullable) {
            return "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(column)
                    + " SET DATA TYPE " + columnType;
        }
    },
    MYSQL("`") {
        @Override
        String renameTableSql(String oldName, String newName) {
            return "RENAME TABLE " + quote(oldName) + " TO " + quote(newName);
        }

        @Override
        String setNotNullSql(String table, String column, String columnType) {
            return "ALTER TABLE " + quote(table) + " MODIFY COLUMN " + quote(column)
                    + " " + columnType + " NOT NULL";
        }

        @Override
        String changeTypeSql(String table, String column, String columnType, boolean nullable) {
            return "ALTER TABLE " + quote(table) + " MODIFY COLUMN " + quote(column)
                    + " " + columnType + (nullable ? " NULL" : " NOT NULL");
        }
    };

    private final String identifierQuote;

    DatabaseDialect(String identifierQuote) {
        this.identifierQuote = identifierQuote;
    }

    static DatabaseDialect fromProductName(String productName) {
        String normalized = productName.toLowerCase(Locale.ROOT);
        if (normalized.equals("h2")) {
            return H2;
        }
        if (normalized.contains("mysql")) {
            return MYSQL;
        }
        throw new IllegalStateException("Unsupported database product for canonical migrations: " + productName);
    }

    String quote(String identifier) {
        return identifierQuote + identifier.replace(identifierQuote, identifierQuote + identifierQuote)
                + identifierQuote;
    }

    abstract String renameTableSql(String oldName, String newName);

    abstract String setNotNullSql(String table, String column, String columnType);

    abstract String changeTypeSql(String table, String column, String columnType, boolean nullable);
}
