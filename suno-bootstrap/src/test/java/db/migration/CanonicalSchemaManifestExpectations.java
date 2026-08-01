package db.migration;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Test-only projection of the migration manifest into dialect-neutral schema assertions. */
public final class CanonicalSchemaManifestExpectations {

    private CanonicalSchemaManifestExpectations() {
    }

    public static ExpectedSchema expectedSchema() {
        Set<String> columns = new TreeSet<>();
        Set<String> uniqueKeys = new TreeSet<>();
        Set<String> foreignKeys = new TreeSet<>();
        Set<String> indexes = new TreeSet<>();
        Set<String> tables = new TreeSet<>();
        for (CanonicalSchemaManifest.TableSpec table : CanonicalSchemaManifest.tables()) {
            tables.add(table.name());
            table.columns().forEach(column -> columns.add(table.name() + "." + column.name()));
            table.uniques().forEach(unique -> uniqueKeys.add(table.name() + "." + columns(unique.columns())));
            table.foreignKeys().forEach(foreignKey -> {
                for (int index = 0; index < foreignKey.columns().size(); index++) {
                    foreignKeys.add(table.name() + "." + foreignKey.columns().get(index) + "->"
                            + foreignKey.referencedTable() + "." + foreignKey.referencedColumns().get(index));
                }
            });
            table.indexes().forEach(index -> indexes.add(table.name() + ".false." + columns(index.columns())));
        }
        return new ExpectedSchema(tables, columns, uniqueKeys, foreignKeys, indexes);
    }

    private static String columns(List<String> columns) {
        return String.join(",", columns);
    }

    public record ExpectedSchema(
            Set<String> tables,
            Set<String> columns,
            Set<String> uniqueKeys,
            Set<String> foreignKeys,
            Set<String> indexes
    ) {
    }
}
