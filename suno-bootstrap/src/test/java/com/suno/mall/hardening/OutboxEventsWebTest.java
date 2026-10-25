package com.suno.mall.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.dao.EventOutboxRepository;
import com.suno.mall.service.RecycleApplicationService;
import com.suno.mall.controller.EventOutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MKT-E004/E005、REC-E004 发布验证：取消/确认收货/回收入账后 outbox 出现对应事件。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxEventsWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecycleApplicationService recycleApplicationService;

    @Autowired
    private EventOutboxRepository eventOutboxRepository;

    @Autowired
    private EventOutboxDispatcher eventOutboxDispatcher;

    @Test
    void lifecycleEventsReachOutbox() throws Exception {
        long nonce = System.nanoTime();
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.suno.mall.controller.AuthController.RegisterRequest("oev_" + nonce, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(register.getResponse().getContentAsString()).at("/data/accessToken").asText();
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        long userId = objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();

        // REC-E004：回收入账
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-OEV-" + nonce, "https://cdn.suno.test/oev.jpg", 90, 0);
        String recycleOrderNo = (String) recycle.get("orderNo");
        recycleApplicationService.transitionOrder(recycleOrderNo, "QUALITY_CHECK", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "PRICE_REVIEW", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "LIST_ON_SHELF", null);
        Long listingId = ((Number) recycleApplicationService.publishResaleListing(recycleOrderNo, new BigDecimal("999.00"), 1)
                .get("listingId")).longValue();

        // MKT-E004：下单后取消
        MvcResult order = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingId))))
                .andExpect(status().isOk()).andReturn();
        String orderNo = objectMapper.readTree(order.getResponse().getContentAsString()).at("/data/orderNo").asText();
        mockMvc.perform(post("/api/mall/orders/cancel").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderNo", orderNo))))
                .andExpect(status().isOk());

        // MKT-E005：再下一单 → 支付 → 发货 → 确认收货
        MvcResult order2 = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingId))))
                .andExpect(status().isOk()).andReturn();
        String orderNo2 = objectMapper.readTree(order2.getResponse().getContentAsString()).at("/data/orderNo").asText();
        recycleApplicationService.markResaleOrderPaidWithIdempotency(orderNo2, "pay-oev-" + nonce);
        recycleApplicationService.deliverResaleOrder(orderNo2);
        mockMvc.perform(post("/api/mall/orders/confirm-receipt").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderNo", orderNo2))))
                .andExpect(status().isOk());

        Set<String> published = eventOutboxRepository.findAll().stream()
                .map(entry -> entry.getUseCaseId())
                .collect(Collectors.toSet());
        assertTrue(published.contains("REC-E004"), "回收积分入账应发布 REC-E004");
        assertTrue(published.contains("REC-E001"), "回收提交应发布 REC-E001");
        assertTrue(published.contains("IDN-E001"), "注册即登录应发布 IDN-E001");
        assertTrue(published.contains("MKT-E001"), "下单应发布 MKT-E001 库存预留");
        assertTrue(published.contains("MKT-E003"), "支付应发布 MKT-E003 商城侧确认");
        assertTrue(published.contains("REC-E002"), "审核应发布 REC-E002");
        assertTrue(published.contains("REC-E005"), "上架应发布 REC-E005");
        assertTrue(published.contains("MKT-E004"), "取消订单应发布 MKT-E004");
        assertTrue(published.contains("MKT-E005"), "确认收货应发布 MKT-E005");

        // 派发器可全部消费
        eventOutboxDispatcher.dispatchPending();
        assertTrue(eventOutboxRepository.findAll().stream().noneMatch(entry -> "PENDING".equals(entry.getStatus())));
    }
}
