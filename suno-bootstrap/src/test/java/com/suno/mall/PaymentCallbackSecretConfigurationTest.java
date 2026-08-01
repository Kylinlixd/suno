package com.suno.mall;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "payment.callback.secret=runtime-supplied-test-secret")
class PaymentCallbackSecretConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void documentedStartupSuppliesAnExternalPaymentCallbackSecret() throws Exception {
        String applicationConfiguration = Files.readString(Path.of("src", "main", "resources", "application.yml"));
        String testApplication = Files.readString(Path.of("src", "test", "resources", "application-test.yml"));
        String readme = Files.readString(Path.of("..", "README.md"));

        assertTrue(applicationConfiguration.contains("secret: ${PAYMENT_CALLBACK_SECRET}"));
        assertTrue(testApplication.contains("secret: test-payment-callback-secret"));
        assertTrue(readme.contains("PAYMENT_CALLBACK_SECRET"));
        assertTrue(readme.contains("./mvnw -pl suno-bootstrap -am package -DskipUnitTests=true"));
        assertTrue(readme.contains("java -jar suno-bootstrap/target/suno-bootstrap-0.0.1-SNAPSHOT.jar"));

        Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();
        String buildOutput = run(repositoryRoot,
                List.of("./mvnw", "-pl", "suno-bootstrap", "-am", "package", "-DskipUnitTests=true"));
        assertTrue(buildOutput.contains("BUILD SUCCESS"), buildOutput);

        ProcessBuilder applicationCommand = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar",
                repositoryRoot.resolve("suno-bootstrap/target/suno-bootstrap-0.0.1-SNAPSHOT.jar").toString(),
                "--server.port=0",
                "--spring.task.scheduling.enabled=false")
                .directory(repositoryRoot.toFile())
                .redirectErrorStream(true);
        applicationCommand.environment().put("PAYMENT_CALLBACK_SECRET", "command-smoke-test-secret");
        Process application = applicationCommand.start();
        try {
            assertTrue(waitForStartup(application), "documented command did not start the application");
        } finally {
            application.destroy();
            if (!application.waitFor(10, TimeUnit.SECONDS)) {
                application.destroyForcibly();
            }
        }
    }

    @Test
    void applicationStartsWhenTheCallbackSecretIsSuppliedExternally() {
        assertNotNull(applicationContext.getBean(RecycleMallApplication.class));
    }

    private static String run(Path workingDirectory, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), () -> "timed out: " + command);
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.exitValue(), output);
        return output;
    }

    private static boolean waitForStartup(Process process) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (Instant.now().isBefore(deadline)) {
                while (reader.ready()) {
                    output.append(reader.readLine()).append(System.lineSeparator());
                }
                if (output.toString().contains("Started RecycleMallApplication")) {
                    return true;
                }
                if (!process.isAlive()) {
                    return false;
                }
                Thread.sleep(100);
            }
        }
        return false;
    }
}
