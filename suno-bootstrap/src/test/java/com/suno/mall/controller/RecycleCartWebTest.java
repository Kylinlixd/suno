package com.suno.mall.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.service.BookScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回收购物车 Web 测试：扫码估价添加、手动添加、移除、提交。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecycleCartWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookScanService bookScanService;

    @BeforeEach
    void setUp() {
        when(bookScanService.scanByIsbn("9780306406157")).thenReturn(Map.of(
                "barcode", "9780306406157",
                "title", "Dune",
                "author", "Frank Herbert",
                "publisher", "Penguin Books",
                "publishYear", 1965
        ));
        when(bookScanService.estimatePrice(1965, 85)).thenReturn(Map.of(
                "estimatedPrice", new BigDecimal("42.00"),
                "grade", "GOOD"
        ));
    }

    private String registerAndLogin(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest("rc_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    @Test
    void scanAndAddBookAndSubmitCart() throws Exception {
        String token = registerAndLogin("scan_" + System.nanoTime());

        // 扫码估价添加
        MvcResult scanResult = mockMvc.perform(post("/api/recycle/scan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isbn\":\"9780306406157\",\"wearScore\":85}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode scanData = objectMapper.readTree(scanResult.getResponse().getContentAsString()).get("data");
        assertEquals("Dune", scanData.get("title").asText());
        assertEquals(42.00, scanData.get("estimatedPrice").asDouble());
        assertEquals("GOOD", scanData.get("grade").asText());

        // 查看回收车
        MvcResult cartResult = mockMvc.perform(get("/api/recycle/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cart = objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("data");
        assertEquals(1, cart.size());

        // 提交回收车
        MvcResult submitResult = mockMvc.perform(post("/api/recycle/cart/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode submitData = objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("data");
        assertEquals(1, submitData.get("submittedCount").asInt());
        assertEquals(1, submitData.get("orderNumbers").size());
        assertEquals(42.00, submitData.get("totalEstimatedPrice").asDouble());
        assertTrue(submitData.get("totalPoints").asInt() > 0);

        // 提交后回收车应为空
        MvcResult cartAfter = mockMvc.perform(get("/api/recycle/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cartAfterData = objectMapper.readTree(cartAfter.getResponse().getContentAsString()).get("data");
        assertEquals(0, cartAfterData.size());
    }

    @Test
    void manualAddAndSubmit() throws Exception {
        String token = registerAndLogin("manual_" + System.nanoTime());

        // 手动添加
        MvcResult addResult = mockMvc.perform(post("/api/recycle/cart")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"旧毛衣\",\"category\":\"衣物\",\"wearScore\":75,\"estimatedPrice\":25.00}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode addData = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("data");
        assertEquals("旧毛衣", addData.get("title").asText());
        assertEquals("衣物", addData.get("category").asText());
        assertEquals("MANUAL", addData.get("source").asText());

        // 提交
        MvcResult submitResult = mockMvc.perform(post("/api/recycle/cart/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode submitData = objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("data");
        assertEquals(1, submitData.get("submittedCount").asInt());
        assertEquals(25.00, submitData.get("totalEstimatedPrice").asDouble());
    }

    @Test
    void removeFromCart() throws Exception {
        String token = registerAndLogin("remove_" + System.nanoTime());

        // 添加
        mockMvc.perform(post("/api/recycle/cart")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"旧沙发\",\"category\":\"家居\",\"wearScore\":60,\"estimatedPrice\":80.00}"))
                .andExpect(status().isOk());

        // 查看回收车获取 itemId
        MvcResult cartResult = mockMvc.perform(get("/api/recycle/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cart = objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("data");
        assertEquals(1, cart.size());
        long itemId = cart.get(0).get("cartItemId").asLong();

        // 移除
        mockMvc.perform(delete("/api/recycle/cart/" + itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 验证回收车为空
        MvcResult cartAfter = mockMvc.perform(get("/api/recycle/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cartAfterData = objectMapper.readTree(cartAfter.getResponse().getContentAsString()).get("data");
        assertEquals(0, cartAfterData.size());
    }
}
