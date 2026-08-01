package com.suno.mall.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("flywayReadiness")
public class FlywayReadinessHealthIndicator implements HealthIndicator {

    private final Flyway flyway;

    public FlywayReadinessHealthIndicator(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public Health health() {
        try {
            boolean validationSuccessful = flyway.validateWithResult().validationSuccessful;
            int appliedMigrations = flyway.info().applied().length;
            if (!validationSuccessful || appliedMigrations == 0) {
                return Health.down()
                        .withDetail("validationSuccessful", validationSuccessful)
                        .withDetail("appliedMigrations", appliedMigrations)
                        .build();
            }
            return Health.up()
                    .withDetail("validationSuccessful", true)
                    .withDetail("appliedMigrations", appliedMigrations)
                    .build();
        } catch (RuntimeException exception) {
            return Health.down(exception).build();
        }
    }
}
