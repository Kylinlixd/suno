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

    @Test
    void mysqlStagingAndProdProfilesPassTinyintOneAsNumericToConnectorJ() {
        for (String profile : List.of("mysql", "staging", "prod")) {
            ApplicationContextRunner runner = profileRunner(profile);

            runner.run(context -> assertThat(context.getEnvironment().getProperty(
                    "spring.datasource.hikari.data-source-properties.tinyInt1isBit"))
                    .as("Connector/J tinyInt1isBit property for profile %s", profile)
                    .isEqualTo("false"));
        }
    }

    @Test
    void stagingAndProdProfilesRequireExternalMysqlAndRealProviders() {
        for (String profile : List.of("staging", "prod")) {
            ApplicationContextRunner runner = profileRunner(profile);
            runner.run(context -> {
                assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                        .as("datasource URL for profile %s", profile)
                        .isEqualTo("jdbc:mysql://database.example/suno");
                assertThat(context.getEnvironment().getProperty("provider.image-audit.mode"))
                        .as("image audit mode for profile %s", profile)
                        .isEqualTo("real");
                assertThat(context.getEnvironment().getProperty("provider.logistics.mode"))
                        .as("logistics mode for profile %s", profile)
                        .isEqualTo("real");
            });
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

    private static ApplicationContextRunner profileRunner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=" + profile,
                        "SUNO_DB_URL=jdbc:mysql://database.example/suno",
                        "SUNO_DB_USERNAME=suno",
                        "SUNO_DB_PASSWORD=not-a-real-password",
                        "BAIDU_IMAGE_AUDIT_ENDPOINT=https://image-audit.example",
                        "BAIDU_IMAGE_AUDIT_ACCESS_TOKEN=not-a-real-token",
                        "LOGISTICS_ENDPOINT=https://logistics.example",
                        "LOGISTICS_API_KEY=not-a-real-key");
    }
}
