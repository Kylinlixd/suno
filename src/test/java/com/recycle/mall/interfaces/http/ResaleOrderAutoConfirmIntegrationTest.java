package com.recycle.mall.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.recycle.mall.infrastructure.entity.OperationAuditLogEntity;
import com.recycle.mall.infrastructure.entity.ProductEntity;
import com.recycle.mall.infrastructure.entity.RecycleOrderEntity;
import com.recycle.mall.infrastructure.entity.ResaleListingEntity;
import com.recycle.mall.infrastructure.entity.ResaleOrderEntity;
import com.recycle.mall.infrastructure.entity.UserAccountEntity;
import com.recycle.mall.infrastructure.repository.OperationAuditLogRepository;
import com.recycle.mall.infrastructure.repository.ProductRepository;
import com.recycle.mall.infrastructure.repository.RecycleOrderRepository;
import com.recycle.mall.infrastructure.repository.ResaleListingRepository;
import com.recycle.mall.infrastructure.repository.ResaleOrderRepository;
import com.recycle.mall.infrastructure.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResaleOrderAutoConfirmIntegrationTest extends TestAuthSupport {

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private RecycleOrderRepository recycleOrderRepository;
    @Autowired
    private ResaleListingRepository resaleListingRepository;
    @Autowired
    private ResaleOrderRepository resaleOrderRepository;
    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Test
    void shouldAutoConfirmOnlyOrdersDeliveredBeforeThreshold() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        OrderSeed oldDelivered = createDeliveredOrderSeed("alice");
        OrderSeed recentDelivered = createDeliveredOrderSeed("alice");
        createDeliverLog(oldDelivered.orderNo(), 120);
        createDeliverLog(recentDelivered.orderNo(), 10);
        String requestId = "req-auto-confirm-" + System.currentTimeMillis();

        String body = """
                {
                  "confirmAfterMinutes":60,
                  "batchSize":10,
                  "requestId":"%s",
                  "changeSummary":{"changedCount":1,"changedKeys":["fulfillStatus"]}
                }
                """.formatted(requestId);
        MvcResult triggerResult = mockMvc.perform(post("/api/admin/recycle/resale-orders/auto-confirm-receipt")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode triggerRoot = objectMapper.readTree(triggerResult.getResponse().getContentAsString());
        assertEquals(1, triggerRoot.get("data").get("confirmedCount").asInt());

        MvcResult oldTrack = mockMvc.perform(get("/api/mall/orders/" + oldDelivered.orderNo() + "/track")
                        .param("buyerUserId", String.valueOf(oldDelivered.buyerUserId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode oldTrackRoot = objectMapper.readTree(oldTrack.getResponse().getContentAsString());
        assertEquals("COMPLETED", oldTrackRoot.get("data").get("fulfillStatus").asText());
        assertTrue(containsAction(oldTrackRoot.get("data").get("timeline"), "RESALE_ORDER_AUTO_RECEIVE"));

        MvcResult recentTrack = mockMvc.perform(get("/api/mall/orders/" + recentDelivered.orderNo() + "/track")
                        .param("buyerUserId", String.valueOf(recentDelivered.buyerUserId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode recentTrackRoot = objectMapper.readTree(recentTrack.getResponse().getContentAsString());
        assertEquals("DELIVERED", recentTrackRoot.get("data").get("fulfillStatus").asText());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_ORDER_AUTO_RECEIVE")
                        .param("targetId", oldDelivered.orderNo())
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode item : logs) {
            if (item.get("detail").asText().contains("requestId=" + requestId)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void shouldAutoGenerateRequestIdWhenMissingForManualAutoConfirm() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        OrderSeed oldDelivered = createDeliveredOrderSeed("alice");
        createDeliverLog(oldDelivered.orderNo(), 120);

        String body = """
                {
                  "confirmAfterMinutes":60,
                  "batchSize":10,
                  "changeSummary":{"changedCount":1,"changedKeys":["fulfillStatus"]}
                }
                """;
        mockMvc.perform(post("/api/admin/recycle/resale-orders/auto-confirm-receipt")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_ORDER_AUTO_RECEIVE")
                        .param("targetId", oldDelivered.orderNo())
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode item : logs) {
            String detail = item.get("detail").asText();
            if (detail.contains("fulfillStatus=COMPLETED") && hasAutoGeneratedRequestId(detail)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void shouldAutoGenerateRequestIdWhenBlankForManualAutoConfirm() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        OrderSeed oldDelivered = createDeliveredOrderSeed("alice");
        createDeliverLog(oldDelivered.orderNo(), 120);

        String body = """
                {
                  "confirmAfterMinutes":60,
                  "batchSize":10,
                  "requestId":"   ",
                  "changeSummary":{"changedCount":1,"changedKeys":["fulfillStatus"]}
                }
                """;
        mockMvc.perform(post("/api/admin/recycle/resale-orders/auto-confirm-receipt")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_ORDER_AUTO_RECEIVE")
                        .param("targetId", oldDelivered.orderNo())
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode item : logs) {
            String detail = item.get("detail").asText();
            if (detail.contains("fulfillStatus=COMPLETED") && hasAutoGeneratedRequestId(detail)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    private boolean containsAction(JsonNode timeline, String actionType) {
        for (JsonNode item : timeline) {
            if (actionType.equals(item.get("actionType").asText())) {
                return true;
            }
        }
        return false;
    }

    private void createDeliverLog(String orderNo, int minutesAgo) {
        OperationAuditLogEntity log = new OperationAuditLogEntity();
        log.setActionType("RESALE_ORDER_DELIVER");
        log.setTargetType("RESALE_ORDER");
        log.setTargetId(orderNo);
        log.setDetail("fulfillStatus=DELIVERED");
        log.setCreatedAt(LocalDateTime.now().minusMinutes(minutesAgo));
        operationAuditLogRepository.save(log);
    }

    private OrderSeed createDeliveredOrderSeed(String buyerUsername) {
        UserAccountEntity buyer = userAccountRepository.findByUsername(buyerUsername)
                .orElseThrow(() -> new IllegalArgumentException("buyer not found: " + buyerUsername));

        ProductEntity product = new ProductEntity();
        product.setSnCode("SN-AUTO-RCV-" + UUID.randomUUID().toString().substring(0, 8));
        product.setBrand("AutoRecvBrand");
        product.setModel("AutoRecvModel");
        product.setProductionDate(LocalDate.of(2024, 1, 1));
        product.setImageUrl("https://example.com/auto-recv.jpg");
        product.setWearScore(93);
        product.setRecycleGrade("GOOD");
        product.setEstimatedRecyclePrice(new BigDecimal("850.00"));
        productRepository.save(product);

        RecycleOrderEntity recycleOrder = new RecycleOrderEntity();
        recycleOrder.setOrderNo("RCY-AUTO-RCV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        recycleOrder.setUser(buyer);
        recycleOrder.setProduct(product);
        recycleOrder.setEstimatedPrice(new BigDecimal("850.00"));
        recycleOrder.setGrade("GOOD");
        recycleOrder.setStatus("LISTED");
        recycleOrder.setCreatedAt(LocalDateTime.now().minusDays(3));
        recycleOrderRepository.save(recycleOrder);

        ResaleListingEntity listing = new ResaleListingEntity();
        listing.setRecycleOrder(recycleOrder);
        listing.setProduct(product);
        listing.setSalePrice(new BigDecimal("1050.00"));
        listing.setStock(1);
        listing.setStatus("ON_SHELF");
        listing.setCreatedAt(LocalDateTime.now().minusDays(3));
        resaleListingRepository.save(listing);

        String orderNo = "B2C-AUTO-RCV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ResaleOrderEntity order = new ResaleOrderEntity();
        order.setOrderNo(orderNo);
        order.setBuyerUser(buyer);
        order.setListing(listing);
        order.setAmount(new BigDecimal("1050.00"));
        order.setPayStatus("PAID");
        order.setFulfillStatus("DELIVERED");
        order.setCreatedAt(LocalDateTime.now().minusDays(2));
        resaleOrderRepository.save(order);
        return new OrderSeed(orderNo, buyer.getId());
    }

    private record OrderSeed(String orderNo, Long buyerUserId) {
    }
}
