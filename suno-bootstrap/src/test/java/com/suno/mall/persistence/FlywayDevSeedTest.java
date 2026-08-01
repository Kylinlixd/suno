package com.suno.mall.persistence;

import org.flywaydb.core.Flyway;
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

        Flyway secondStart = devFlyway(jdbcUrl);
        secondStart.migrate();

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
}
