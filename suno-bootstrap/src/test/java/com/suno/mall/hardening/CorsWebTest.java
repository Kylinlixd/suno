package com.suno.mall.hardening;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 跨域契约：白名单来源（前台 4173 / 后台 4174）预检放行并回显来源；未知来源拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightFromAllowedOriginsIsPermitted() throws Exception {
        for (String origin : new String[]{"http://localhost:4173", "http://localhost:4174"}) {
            MvcResult result = mockMvc.perform(options("/api/mall/listings")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andReturn();
            assertEquals(origin, result.getResponse().getHeader("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void unknownOriginGetsNoAllowHeader() throws Exception {
        MvcResult result = mockMvc.perform(options("/api/mall/listings")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andReturn();
        assertNull(result.getResponse().getHeader("Access-Control-Allow-Origin"), "未知来源不得获得跨域许可");
    }
}
