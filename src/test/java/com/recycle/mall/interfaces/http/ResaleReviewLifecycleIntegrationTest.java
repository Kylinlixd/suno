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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResaleReviewLifecycleIntegrationTest extends TestAuthSupport {

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
    void shouldRejectCreateReviewWhenOrderNotCompleted() throws Exception {
        OrderSeed seed = createOrderSeed("alice", "PAID", "DELIVERED");
        String body = """
                {
                  "orderNo":"%s",
                  "buyerUserId":%d,
                  "rating":5,
                  "content":"物流快，包装好",
                  "imageUrls":["https://example.com/a.jpg"]
                }
                """.formatted(seed.orderNo(), seed.buyerUserId());
        MvcResult result = mockMvc.perform(post("/api/mall/reviews/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains("仅 COMPLETED 订单可评价"));
    }

    @Test
    void shouldCreateReviewWhenOrderCompleted() throws Exception {
        OrderSeed seed = createOrderSeed("alice", "PAID", "COMPLETED");
        String body = """
                {
                  "orderNo":"%s",
                  "buyerUserId":%d,
                  "rating":5,
                  "content":"确认收货后评价，整体满意",
                  "imageUrls":["https://example.com/b.jpg"]
                }
                """.formatted(seed.orderNo(), seed.buyerUserId());
        MvcResult result = mockMvc.perform(post("/api/mall/reviews/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
    }

    @Test
    void shouldRejectAppendReviewWhenCompletedOrderOutOfWindow() throws Exception {
        OrderSeed seed = createOrderSeed("alice", "PAID", "COMPLETED");
        createCompleteLog(seed.orderNo(), 45, "RESALE_ORDER_AUTO_RECEIVE");
        createReview(seed.orderNo(), seed.buyerUserId(), "先给个初评");

        String appendBody = """
                {
                  "orderNo":"%s",
                  "buyerUserId":%d,
                  "appendContent":"超时追评尝试"
                }
                """.formatted(seed.orderNo(), seed.buyerUserId());
        MvcResult result = mockMvc.perform(post("/api/mall/reviews/append")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appendBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains("已超过追评窗口"));
    }

    @Test
    void shouldAllowAppendReviewWithinWindow() throws Exception {
        OrderSeed seed = createOrderSeed("alice", "PAID", "COMPLETED");
        createCompleteLog(seed.orderNo(), 2, "RESALE_ORDER_RECEIVE");
        createReview(seed.orderNo(), seed.buyerUserId(), "窗口内初评");

        String appendBody = """
                {
                  "orderNo":"%s",
                  "buyerUserId":%d,
                  "appendContent":"窗口内追评成功"
                }
                """.formatted(seed.orderNo(), seed.buyerUserId());
        MvcResult result = mockMvc.perform(post("/api/mall/reviews/append")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appendBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
    }

    @Test
    void shouldReturnReviewEligibilityInOrderTrack() throws Exception {
        OrderSeed seed = createOrderSeed("alice", "PAID", "COMPLETED");
        createCompleteLog(seed.orderNo(), 1, "RESALE_ORDER_RECEIVE");

        MvcResult beforeReviewTrack = mockMvc.perform(get("/api/mall/orders/" + seed.orderNo() + "/track")
                        .param("buyerUserId", String.valueOf(seed.buyerUserId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode beforeRoot = objectMapper.readTree(beforeReviewTrack.getResponse().getContentAsString());
        JsonNode beforeEligibility = beforeRoot.get("data").get("reviewEligibility");
        assertTrue(beforeEligibility.get("canCreateReview").asBoolean());
        assertFalse(beforeEligibility.get("hasReviewed").asBoolean());
        assertFalse(beforeEligibility.get("canAppendReview").asBoolean());
        assertNotNull(beforeEligibility.get("appendDeadline"));
        assertTrue(beforeEligibility.get("appendRemainingHours").asLong() > 0);
        assertTrue(beforeEligibility.get("appendRemainingDays").asLong() >= 0);
        assertTrue(beforeEligibility.get("appendRemainingText").asText().contains("还可追评"));
        assertTrue(beforeEligibility.get("appendRemainingTextI18n").has("zh-CN"));
        assertTrue(beforeEligibility.get("appendRemainingTextI18n").has("en-US"));

        createReview(seed.orderNo(), seed.buyerUserId(), "先评价再看资格");
        MvcResult afterReviewTrack = mockMvc.perform(get("/api/mall/orders/" + seed.orderNo() + "/track")
                        .param("buyerUserId", String.valueOf(seed.buyerUserId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterRoot = objectMapper.readTree(afterReviewTrack.getResponse().getContentAsString());
        JsonNode afterEligibility = afterRoot.get("data").get("reviewEligibility");
        assertFalse(afterEligibility.get("canCreateReview").asBoolean());
        assertTrue(afterEligibility.get("hasReviewed").asBoolean());
        assertTrue(afterEligibility.get("canAppendReview").asBoolean());
        assertEquals(30, afterEligibility.get("appendWindowDays").asInt());
        assertTrue(afterEligibility.get("appendRemainingHours").asLong() > 0);
        assertTrue(afterEligibility.get("appendRemainingText").asText().contains("还可追评"));
        assertTrue(afterEligibility.get("appendRemainingTextI18n").get("en-US").asText().contains("Append review available"));
    }

    private void createReview(String orderNo, Long buyerUserId, String content) throws Exception {
        String body = """
                {
                  "orderNo":"%s",
                  "buyerUserId":%d,
                  "rating":5,
                  "content":"%s",
                  "imageUrls":[]
                }
                """.formatted(orderNo, buyerUserId, content);
        mockMvc.perform(post("/api/mall/reviews/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void createCompleteLog(String orderNo, int daysAgo, String actionType) {
        OperationAuditLogEntity log = new OperationAuditLogEntity();
        log.setActionType(actionType);
        log.setTargetType("RESALE_ORDER");
        log.setTargetId(orderNo);
        log.setDetail("fulfillStatus=COMPLETED");
        log.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        operationAuditLogRepository.save(log);
    }

    private OrderSeed createOrderSeed(String buyerUsername, String payStatus, String fulfillStatus) {
        UserAccountEntity buyer = userAccountRepository.findByUsername(buyerUsername)
                .orElseThrow(() -> new IllegalArgumentException("buyer not found: " + buyerUsername));

        ProductEntity product = new ProductEntity();
        product.setSnCode("SN-RVW-" + UUID.randomUUID().toString().substring(0, 8));
        product.setBrand("ReviewBrand");
        product.setModel("ReviewModel");
        product.setProductionDate(LocalDate.of(2024, 1, 1));
        product.setImageUrl("https://example.com/review.jpg");
        product.setWearScore(92);
        product.setRecycleGrade("GOOD");
        product.setEstimatedRecyclePrice(new BigDecimal("800.00"));
        productRepository.save(product);

        RecycleOrderEntity recycleOrder = new RecycleOrderEntity();
        recycleOrder.setOrderNo("RCY-RVW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        recycleOrder.setUser(buyer);
        recycleOrder.setProduct(product);
        recycleOrder.setEstimatedPrice(new BigDecimal("800.00"));
        recycleOrder.setGrade("GOOD");
        recycleOrder.setStatus("LISTED");
        recycleOrder.setCreatedAt(LocalDateTime.now());
        recycleOrderRepository.save(recycleOrder);

        ResaleListingEntity listing = new ResaleListingEntity();
        listing.setRecycleOrder(recycleOrder);
        listing.setProduct(product);
        listing.setSalePrice(new BigDecimal("980.00"));
        listing.setStock(1);
        listing.setStatus("ON_SHELF");
        listing.setCreatedAt(LocalDateTime.now());
        resaleListingRepository.save(listing);

        String orderNo = "B2C-RVW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ResaleOrderEntity order = new ResaleOrderEntity();
        order.setOrderNo(orderNo);
        order.setBuyerUser(buyer);
        order.setListing(listing);
        order.setAmount(new BigDecimal("980.00"));
        order.setPayStatus(payStatus);
        order.setFulfillStatus(fulfillStatus);
        order.setCreatedAt(LocalDateTime.now());
        resaleOrderRepository.save(order);
        return new OrderSeed(orderNo, buyer.getId());
    }

    private record OrderSeed(String orderNo, Long buyerUserId) {
    }
}
