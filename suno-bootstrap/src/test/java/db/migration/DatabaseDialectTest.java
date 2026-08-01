package db.migration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseDialectTest {

    @Test
    void detectsSupportedDatabaseProducts() {
        assertThat(DatabaseDialect.fromProductName("H2")).isEqualTo(DatabaseDialect.H2);
        assertThat(DatabaseDialect.fromProductName("MySQL")).isEqualTo(DatabaseDialect.MYSQL);
        assertThat(DatabaseDialect.fromProductName("MySQL Community Server - GPL"))
                .isEqualTo(DatabaseDialect.MYSQL);
        assertThatThrownBy(() -> DatabaseDialect.fromProductName("PostgreSQL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void quotesIdentifiersAndBuildsDialectCorrectRenameSql() {
        assertThat(DatabaseDialect.H2.quote("legacy_table")).isEqualTo("\"legacy_table\"");
        assertThat(DatabaseDialect.H2.renameTableSql("legacy_table", "suno_legacy_table"))
                .isEqualTo("ALTER TABLE \"legacy_table\" RENAME TO \"suno_legacy_table\"");

        assertThat(DatabaseDialect.MYSQL.quote("legacy_table")).isEqualTo("`legacy_table`");
        assertThat(DatabaseDialect.MYSQL.renameTableSql("legacy_table", "suno_legacy_table"))
                .isEqualTo("RENAME TABLE `legacy_table` TO `suno_legacy_table`");
    }

    @Test
    void buildsDialectCorrectNotNullSql() {
        assertThat(DatabaseDialect.H2.setNotNullSql("suno_listing", "updated_at", "TIMESTAMP"))
                .isEqualTo("ALTER TABLE \"suno_listing\" ALTER COLUMN \"updated_at\" SET NOT NULL");
        assertThat(DatabaseDialect.MYSQL.setNotNullSql("suno_listing", "updated_at", "TIMESTAMP"))
                .isEqualTo("ALTER TABLE `suno_listing` MODIFY COLUMN `updated_at` TIMESTAMP NOT NULL");
    }

    @Test
    void buildsDialectCorrectTypeChangeSqlWithoutDroppingTheColumn() {
        assertThat(DatabaseDialect.H2.changeTypeSql("suno_task", "content_text", "VARCHAR(255)", true))
                .isEqualTo("ALTER TABLE \"suno_task\" ALTER COLUMN \"content_text\" SET DATA TYPE VARCHAR(255)");
        assertThat(DatabaseDialect.MYSQL.changeTypeSql("suno_task", "content_text", "VARCHAR(255)", true))
                .isEqualTo("ALTER TABLE `suno_task` MODIFY COLUMN `content_text` VARCHAR(255) NULL");
    }
}
