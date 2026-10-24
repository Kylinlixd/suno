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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2 售后（MKT-060~062）与站内通知（OPS-050~053）Web 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefundNotificationWebTest {

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
                                new AuthController.RegisterRequest("refund_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private Long currentUserId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();
    }

    /** 创建并支付一笔订单，返回 orderNo */
    private String createPaidOrder(String token, String snSuffix) throws Exception {
        Long userId = currentUserId(token);
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-REFUND-" + snSuffix, "https://cdn.suno.test/" + snSuffix + ".jpg", 90, 0);
        String recycleOrderNo = (String) recycle.get("orderNo");
        recycleApplicationService.transitionOrder(recycleOrderNo, "QUALITY_CHECK", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "PRICE_REVIEW", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "LIST_ON_SHELF", null);
        Long listingId = ((Number) recycleApplicationService.publishResaleListing(recycleOrderNo, new BigDecimal("1599.00"), 1)
                .get("listingId")).longValue();
        MvcResult order = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingId))))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(order.getResponse().getContentAsString()).at("/data/orderNo").asText();
        recycleApplicationService.markResaleOrderPaidWithIdempotency(orderNo, "pay-" + UUID.randomUUID());
        return orderNo;
    }

    @Test
    void refundApplyListCancelRoundTrip() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("rt_" + nonce);
        String orderNo = createPaidOrder(token, "R" + nonce);

        MvcResult apply = mockMvc.perform(post("/api/mall/orders/" + orderNo + "/refund-apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundController.RefundApplyRequest("商品与描述不符，希望退款", null))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode applyData = objectMapper.readTree(apply.getResponse().getContentAsString()).get("data");
        assertEquals("PENDING", applyData.get("status").asText());

        // 同一订单重复申请被拒
        MvcResult duplicate = mockMvc.perform(post("/api/mall/orders/" + orderNo + "/refund-apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundController.RefundApplyRequest("再次申请售后试试看", null))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("REFUND_ALREADY_OPEN",
                objectMapper.readTree(duplicate.getResponse().getContentAsString()).get("errorCode").asText());

        // 我的售后列表
        MvcResult list = mockMvc.perform(get("/api/mall/refunds").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(list.getResponse().getContentAsString()).at("/data/content");
        assertEquals(1, content.size());
        assertEquals(orderNo, content.get(0).get("orderNo").asText());

        // 撤销售后
        mockMvc.perform(post("/api/mall/refunds/" + content.get(0).get("refundId").asLong() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminRefundSettlesPendingRefundOrder() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("admin_" + nonce);
        String orderNo = createPaidOrder(token, "A" + nonce);
        mockMvc.perform(post("/api/mall/orders/" + orderNo + "/refund-apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefundController.RefundApplyRequest("物流破损，申请退款处理", null))))
                .andExpect(status().isOk());

        recycleApplicationService.refundPaidResaleOrder(orderNo);

        MvcResult list = mockMvc.perform(get("/api/mall/refunds").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(list.getResponse().getContentAsString()).at("/data/content");
        assertEquals("PROCESSED", content.get(0).get("status").asText());
        assertEquals(1599.00, content.get(0).get("amount").asDouble());
    }

    @Test
    void orderLifecycleProducesNotifications() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("notify_" + nonce);
        String orderNo = createPaidOrder(token, "N" + nonce);

        MvcResult unread = mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        int unreadCount = objectMapper.readTree(unread.getResponse().getContentAsString()).at("/data/unreadCount").asInt();
        assertTrue(unreadCount >= 2, "下单与支付至少各产生一条通知，实际 " + unreadCount);

        MvcResult list = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(list.getResponse().getContentAsString()).at("/data/content");
        assertTrue(content.size() >= 2);
        long notificationId = content.get(0).get("id").asLong();

        mockMvc.perform(post("/api/notifications/" + notificationId + "/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        MvcResult afterRead = mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(unreadCount - 1,
                objectMapper.readTree(afterRead.getResponse().getContentAsString()).at("/data/unreadCount").asInt());

        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        MvcResult afterAll = mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, objectMapper.readTree(afterAll.getResponse().getContentAsString()).at("/data/unreadCount").asInt());
    }

    private long extractRefundId(String token, String orderNo) throws Exception {
        MvcResult list = mockMvc.perform(get("/api/mall/refunds").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode row : objectMapper.readTree(list.getResponse().getContentAsString()).at("/data/content")) {
            if (orderNo.equals(row.get("orderNo").asText())) {
                return row.get("refundId").asLong();
            }
        }
        return -1;
    }
}
