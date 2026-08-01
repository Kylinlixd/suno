package com.suno.mall;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "payment.callback.secret=runtime-supplied-test-secret")
class PaymentCallbackSecretConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void documentedStartupSuppliesAnExternalPaymentCallbackSecret() throws Exception {
        String application = Files.readString(Path.of("src", "main", "resources", "application.yml"));
        String testApplication = Files.readString(Path.of("src", "test", "resources", "application-test.yml"));
        String readme = Files.readString(Path.of("..", "README.md"));

        assertTrue(application.contains("secret: ${PAYMENT_CALLBACK_SECRET}"));
        assertTrue(testApplication.contains("secret: test-payment-callback-secret"));
        assertTrue(readme.contains("PAYMENT_CALLBACK_SECRET"));
        assertTrue(readme.contains("./mvnw -pl suno-bootstrap -am spring-boot:run"));
    }

    @Test
    void applicationStartsWhenTheCallbackSecretIsSuppliedExternally() {
        assertNotNull(applicationContext.getBean(RecycleMallApplication.class));
    }
}
