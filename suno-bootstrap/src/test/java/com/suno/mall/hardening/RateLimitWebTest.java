package com.suno.mall.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4 限流过滤器测试：专用上下文把窗口配额降到 3，第 4 次登录返回 429 + RATE_LIMITED。
 */
@SpringBootTest(properties = {
        "mall.rate-limit.enabled=true",
        "mall.rate-limit.limit=3",
        "mall.rate-limit.window-ms=600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void blocksRequestsBeyondWindowQuota() throws Exception {
        // 前三次在配额内：登录失败（凭据错误）具体状态码与本测试无关，只要求未被限流
        for (int attempt = 1; attempt <= 3; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong-password\"}"))
                    .andReturn();
            assertTrue(result.getResponse().getStatus() != 429, "配额内不应被限流");
        }
        MvcResult blocked = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andReturn();
        assertEquals("RATE_LIMITED",
                objectMapper.readTree(blocked.getResponse().getContentAsString()).get("errorCode").asText());
    }
}
