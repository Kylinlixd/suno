package com.suno.mall.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayLocationProfileTest {

    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String DEV_SEED = "classpath:db/dev";

    @Test
    void commonProfileResolvesOnlyVersionedMigrations() {
        assertLocations(null, List.of(MIGRATIONS));
    }

    @Test
    void devProfileAloneResolvesTheRepeatableSeedLocation() {
        assertLocations("dev", List.of(MIGRATIONS, DEV_SEED));
    }

    @Test
    void mysqlStagingProdAndTestProfilesCannotResolveDevSeed() {
        for (String profile : List.of("mysql", "staging", "prod", "test")) {
            assertLocations(profile, List.of(MIGRATIONS));
        }
    }

    private static void assertLocations(String profile, List<String> expected) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer());
        if (profile != null) {
            runner = runner.withPropertyValues("spring.profiles.active=" + profile);
        }
        runner.run(context -> {
            List<String> locations = Binder.get(context.getEnvironment())
                    .bind("spring.flyway.locations", Bindable.listOf(String.class))
                    .orElse(List.of());
            assertThat(locations).as("Flyway locations for profile %s", profile).containsExactlyElementsOf(expected);
            assertThat(locations.contains(DEV_SEED)).isEqualTo("dev".equals(profile));
        });
    }
}
