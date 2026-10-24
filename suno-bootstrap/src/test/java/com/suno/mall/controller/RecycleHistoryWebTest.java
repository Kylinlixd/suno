package com.suno.mall.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.service.RecycleApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REC-003 我的回收历史、REC-004 我的积分流水 Web 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecycleHistoryWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecycleApplicationService recycleApplicationService;

    private String registerAndLogin(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest("history_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private Long currentUserId(String accessToken) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();
    }

    @Test
    void listsMyRecycleOrdersWithProductSummary() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("mine_" + nonce);
        Long userId = currentUserId(token);
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-HISTORY-" + nonce, "https://cdn.suno.test/history-" + nonce + ".jpg", 85, 1);

        MvcResult result = mockMvc.perform(get("/api/recycle/orders/my")
                        .param("page", "0").param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(0, data.get("page").asInt());
        assertTrue(data.get("totalElements").asLong() >= 1);
        JsonNode mine = findOrderByNo(data.get("content"), (String) recycle.get("orderNo"));
        assertTrue(mine != null, "本人回收单应出现在历史中");
        assertEquals("CREATED", mine.get("status").asText());
        assertTrue(mine.has("brand") && mine.has("model"), "历史应携带商品摘要");
        assertTrue(mine.has("image") && !mine.get("image").asText().isBlank());
        assertTrue(mine.get("estimatedPrice").asDouble() > 0);
    }

    @Test
    void isolatesRecycleHistoryBetweenUsers() throws Exception {
        long nonce = System.nanoTime();
        String tokenA = registerAndLogin("iso_a_" + nonce);
        String tokenB = registerAndLogin("iso_b_" + nonce);
        Long userA = currentUserId(tokenA);
        Long userB = currentUserId(tokenB);
        String orderA = (String) recycleApplicationService.createRecycleOrder(
                userA, "SN-ISO-A-" + nonce, "https://cdn.suno.test/iso-a.jpg", 80, 0).get("orderNo");
        String orderB = (String) recycleApplicationService.createRecycleOrder(
                userB, "SN-ISO-B-" + nonce, "https://cdn.suno.test/iso-b.jpg", 80, 0).get("orderNo");

        MvcResult resultA = mockMvc.perform(get("/api/recycle/orders/my")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode contentA = objectMapper.readTree(resultA.getResponse().getContentAsString()).at("/data/content");
        assertTrue(findOrderByNo(contentA, orderA) != null);
        assertFalse(findOrderByNo(contentA, orderB) != null, "不能读到他人回收单");
    }

    @Test
    void listsMyPointsLedgerWithBalance() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("points_" + nonce);
        Long userId = currentUserId(token);
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-POINTS-" + nonce, "https://cdn.suno.test/points.jpg", 80, 0);
        int rewardPoints = ((Number) recycle.get("rewardPoints")).intValue();

        MvcResult result = mockMvc.perform(get("/api/recycle/points/ledger")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.get("totalElements").asLong() >= 1);
        assertEquals(rewardPoints, data.get("balance").asInt(), "余额应等于新用户的入账积分");
        JsonNode first = data.get("content").get(0);
        assertEquals(rewardPoints, first.get("pointsDelta").asInt());
        assertTrue(first.get("reason").asText().startsWith("RECYCLE_ORDER:"));
    }

    @Test
    void rejectsHistoryWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/recycle/orders/my")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recycle/points/ledger")).andExpect(status().isUnauthorized());
    }

    private static JsonNode findOrderByNo(JsonNode content, String orderNo) {
        for (JsonNode item : content) {
            if (orderNo.equals(item.get("orderNo").asText())) {
                return item;
            }
        }
        return null;
    }
}
