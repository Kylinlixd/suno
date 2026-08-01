package com.suno.mall.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayDevSeedTest {

    @Test
    void repeatedDevStartsRetainOneOfEachFallbackValuationRule() throws Exception {
        String jdbcUrl = FlywayH2MigrationTest.h2Url("dev-seed");
        Flyway firstStart = devFlyway(jdbcUrl);
        firstStart.migrate();

        markDevSeedAsChanged(jdbcUrl);
        Flyway secondStart = devFlyway(jdbcUrl);
        MigrateResult secondResult = secondStart.migrate();
        assertThat(secondResult.migrationsExecuted).isOne();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rules = statement.executeQuery("""
                     SELECT min_months, max_months, min_wear_score, max_wear_score, grade, price
                     FROM suno_valuation_rule
                     WHERE brand = 'ALL' AND model = 'ALL'
                     ORDER BY min_months
                     """)) {
            assertThat(rules.next()).isTrue();
            assertThat(rules.getInt("min_months")).isZero();
            assertThat(rules.getInt("max_months")).isEqualTo(18);
            assertThat(rules.getInt("min_wear_score")).isZero();
            assertThat(rules.getInt("max_wear_score")).isEqualTo(100);
            assertThat(rules.getString("grade")).isEqualTo("GOOD");
            assertThat(rules.getBigDecimal("price")).isEqualByComparingTo("1800.00");

            assertThat(rules.next()).isTrue();
            assertThat(rules.getInt("min_months")).isEqualTo(19);
            assertThat(rules.getInt("max_months")).isEqualTo(36);
            assertThat(rules.getString("grade")).isEqualTo("MEDIUM");
            assertThat(rules.getBigDecimal("price")).isEqualByComparingTo("1200.00");

            assertThat(rules.next()).isTrue();
            assertThat(rules.getInt("min_months")).isEqualTo(37);
            assertThat(rules.getInt("max_months")).isEqualTo(240);
            assertThat(rules.getString("grade")).isEqualTo("UNQUALIFIED");
            assertThat(rules.getBigDecimal("price")).isEqualByComparingTo("300.00");
            assertThat(rules.next()).isFalse();
        }
    }

    private static Flyway devFlyway(String jdbcUrl) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration", "classpath:db/dev")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    private static void markDevSeedAsChanged(String jdbcUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate("""
                    UPDATE flyway_schema_history
                    SET checksum = 0
                    WHERE script = 'R__dev_seed.sql'
                    """);
            assertThat(updated).isOne();
        }
    }
}
