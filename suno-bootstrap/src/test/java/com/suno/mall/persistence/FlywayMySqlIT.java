package com.suno.mall.persistence;

import com.suno.mall.testsupport.MySqlContainerSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMySqlIT {

    @Test
    void migratesAnEmptyMysqlDatabaseValidatesHibernateMappingsAndIsIdempotent() {
        Assumptions.assumeTrue(
                MySqlContainerSupport.isDockerAvailable(),
                MySqlContainerSupport.dockerUnavailableEvidence()
        );
        try {
            runMigrationVerification();
        } catch (Exception exception) {
            throw new IllegalStateException("MySQL migration verification failed", exception);
        }
    }

    private static void runMigrationVerification() throws Exception {
        String jdbcUrl = MySqlContainerSupport.createDatabaseUrl("clean");

        Flyway flyway = Flyway.configure()
                .dataSource(
                        jdbcUrl,
                        MySqlContainerSupport.mysql().getUsername(),
                        MySqlContainerSupport.mysql().getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        MigrateResult first = flyway.migrate();
        assertThat(first.success).isTrue();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        validateHibernateMappings(jdbcUrl);

        MigrateResult second = flyway.migrate();
        assertThat(second.success).isTrue();
        assertThat(second.migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    private static void validateHibernateMappings(String jdbcUrl) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                jdbcUrl,
                MySqlContainerSupport.mysql().getUsername(),
                MySqlContainerSupport.mysql().getPassword());
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");

        LocalContainerEntityManagerFactoryBean entityManagerFactory =
                new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan("com.suno.mall.entity");
        entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.jdbc.time_zone", "UTC",
                "hibernate.type.preferred_boolean_jdbc_type", "TINYINT"));
        entityManagerFactory.afterPropertiesSet();
        entityManagerFactory.destroy();
    }
}
