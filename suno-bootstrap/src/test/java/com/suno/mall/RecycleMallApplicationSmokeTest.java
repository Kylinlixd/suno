package com.suno.mall;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RecycleMallApplicationSmokeTest {

    @Autowired
    private RecycleMallApplication application;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void applicationBeanStartsWithTheMinimalH2Profile() {
        assertThat(application).isNotNull();
    }
}
