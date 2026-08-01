package com.suno.mall.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayH2MigrationTest {

    @Test
    void migratesAnEmptyH2DatabaseAndCreatesEveryMappedTable() throws Exception {
        String jdbcUrl = h2Url("empty");
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().all())
                .extracting(MigrationInfo::getVersion)
                .doesNotContainNull();
        assertThat(databaseTables(jdbcUrl)).containsAll(mappedTableNames());
    }

    static String h2Url(String label) {
        return "jdbc:h2:mem:" + label + "-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    static Set<String> mappedTableNames() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<String> names = new TreeSet<>();
        for (var candidate : scanner.findCandidateComponents("com.suno.mall.entity")) {
            Class<?> entityType = Class.forName(candidate.getBeanClassName());
            names.add(entityType.getAnnotation(Table.class).name().toLowerCase(Locale.ROOT));
        }
        assertThat(names).hasSize(28);
        return names;
    }

    static Set<String> databaseTables(String jdbcUrl) throws Exception {
        Set<String> names = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }
}
