package com.recycle.mall.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.recycle.mall.infrastructure.entity.ProductEntity;
import com.recycle.mall.infrastructure.entity.RecycleOrderEntity;
import com.recycle.mall.infrastructure.entity.ResaleListingEntity;
import com.recycle.mall.infrastructure.entity.ResaleOrderEntity;
import com.recycle.mall.infrastructure.entity.UserAccountEntity;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminResaleOrderAuditContextIntegrationTest extends TestAuthSupport {

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

    @Test
    void shouldAutoGenerateRequestIdWhenMissingForDeliver() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        String orderNo = createPaidOrderSeed("alice");

        String body = """
                {
                  "orderNo":"%s",
                  "changeSummary":{"changedCount":1,"changedKeys":["fulfillStatus"]}
                }
                """.formatted(orderNo);
        mockMvc.perform(post("/api/admin/recycle/resale-orders/deliver")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertHasGeneratedRequestIdInAuditLog(admin, "RESALE_ORDER_DELIVER", orderNo, "fulfillStatus=DELIVERED");
    }

    @Test
    void shouldAutoGenerateRequestIdWhenMissingForRefund() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        String orderNo = createPaidOrderSeed("alice");

        String body = """
                {
                  "orderNo":"%s",
                  "changeSummary":{"changedCount":1,"changedKeys":["payStatus","fulfillStatus"]}
                }
                """.formatted(orderNo);
        mockMvc.perform(post("/api/admin/recycle/resale-orders/refund")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertHasGeneratedRequestIdInAuditLog(admin, "RESALE_ORDER_REFUND", orderNo, "payStatus=REFUNDED");
    }

    @Test
    void shouldAutoGenerateRequestIdWhenBlankForDeliverAndRefund() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        String deliverOrderNo = createPaidOrderSeed("alice");
        String refundOrderNo = createPaidOrderSeed("alice");

        String deliverBody = """
                {
                  "orderNo":"%s",
                  "requestId":"   ",
                  "changeSummary":{"changedCount":1,"changedKeys":["fulfillStatus"]}
                }
                """.formatted(deliverOrderNo);
        mockMvc.perform(post("/api/admin/recycle/resale-orders/deliver")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliverBody))
                .andExpect(status().isOk());
        assertHasGeneratedRequestIdInAuditLog(admin, "RESALE_ORDER_DELIVER", deliverOrderNo, "fulfillStatus=DELIVERED");

        String refundBody = """
                {
                  "orderNo":"%s",
                  "requestId":"   ",
                  "changeSummary":{"changedCount":1,"changedKeys":["payStatus","fulfillStatus"]}
                }
                """.formatted(refundOrderNo);
        mockMvc.perform(post("/api/admin/recycle/resale-orders/refund")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody))
                .andExpect(status().isOk());
        assertHasGeneratedRequestIdInAuditLog(admin, "RESALE_ORDER_REFUND", refundOrderNo, "payStatus=REFUNDED");
    }

    private void assertHasGeneratedRequestIdInAuditLog(
            Tokens admin,
            String actionType,
            String targetId,
            String expectedDetailPart
    ) throws Exception {
        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", actionType)
                        .param("targetId", targetId)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode item : logs) {
            String detail = item.get("detail").asText();
            if (detail.contains(expectedDetailPart) && hasAutoGeneratedRequestId(detail)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    private String createPaidOrderSeed(String buyerUsername) {
        UserAccountEntity buyer = userAccountRepository.findByUsername(buyerUsername)
                .orElseThrow(() -> new IllegalArgumentException("buyer not found: " + buyerUsername));

        ProductEntity product = new ProductEntity();
        product.setSnCode("SN-ADMIN-AUDIT-" + UUID.randomUUID().toString().substring(0, 8));
        product.setBrand("AdminAuditBrand");
        product.setModel("AdminAuditModel");
        product.setProductionDate(LocalDate.of(2024, 1, 1));
        product.setImageUrl("https://example.com/admin-audit.jpg");
        product.setWearScore(92);
        product.setRecycleGrade("GOOD");
        product.setEstimatedRecyclePrice(new BigDecimal("860.00"));
        productRepository.save(product);

        RecycleOrderEntity recycleOrder = new RecycleOrderEntity();
        recycleOrder.setOrderNo("RCY-ADMIN-AUDIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        recycleOrder.setUser(buyer);
        recycleOrder.setProduct(product);
        recycleOrder.setEstimatedPrice(new BigDecimal("860.00"));
        recycleOrder.setGrade("GOOD");
        recycleOrder.setStatus("LISTED");
        recycleOrder.setCreatedAt(LocalDateTime.now().minusDays(2));
        recycleOrderRepository.save(recycleOrder);

        ResaleListingEntity listing = new ResaleListingEntity();
        listing.setRecycleOrder(recycleOrder);
        listing.setProduct(product);
        listing.setSalePrice(new BigDecimal("1060.00"));
        listing.setStock(1);
        listing.setStatus("ON_SHELF");
        listing.setCreatedAt(LocalDateTime.now().minusDays(1));
        resaleListingRepository.save(listing);

        ResaleOrderEntity order = new ResaleOrderEntity();
        String orderNo = "B2C-ADMIN-AUDIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setOrderNo(orderNo);
        order.setBuyerUser(buyer);
        order.setListing(listing);
        order.setAmount(new BigDecimal("1060.00"));
        order.setPayStatus("PAID");
        order.setFulfillStatus("TO_DELIVER");
        order.setCreatedAt(LocalDateTime.now().minusHours(3));
        resaleOrderRepository.save(order);
        return orderNo;
    }
}
