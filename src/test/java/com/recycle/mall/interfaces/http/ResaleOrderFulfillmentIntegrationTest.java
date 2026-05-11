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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResaleOrderFulfillmentIntegrationTest extends TestAuthSupport {

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
    void shouldConfirmReceiptWhenOrderDelivered() throws Exception {
        OrderSeed seed = createOrderSeed("alice", "PAID", "DELIVERED");
        String body = """
                {
                  "orderNo":"%s",
                  "buyerUserId":%d
                }
                """.formatted(seed.orderNo(), seed.buyerUserId());
        MvcResult confirmResult = mockMvc.perform(post("/api/mall/orders/confirm-receipt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode confirmRoot = objectMapper.readTree(confirmResult.getResponse().getContentAsString());
        assertTrue(confirmRoot.get("success").asBoolean());
        assertEquals("COMPLETED", confirmRoot.get("data").get("fulfillStatus").asText());

        MvcResult trackResult = mockMvc.perform(get("/api/mall/orders/" + seed.orderNo() + "/track")
                        .param("buyerUserId", String.valueOf(seed.buyerUserId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode trackRoot = objectMapper.readTree(trackResult.getResponse().getContentAsString());
        JsonNode timeline = trackRoot.get("data").get("timeline");
        boolean hasReceiveAction = false;
        for (JsonNode event : timeline) {
            if ("RESALE_ORDER_RECEIVE".equals(event.get("actionType").asText())) {
                hasReceiveAction = true;
                break;
            }
        }
        assertTrue(hasReceiveAction);
    }

    @Test
    void shouldRejectConfirmReceiptWhenOrderNotDelivered() throws Exception {
        OrderSeed seed = createOrderSeed("alice", "PAID", "TO_DELIVER");
        String body = """
                {
                  "orderNo":"%s",
                  "buyerUserId":%d
                }
                """.formatted(seed.orderNo(), seed.buyerUserId());
        MvcResult result = mockMvc.perform(post("/api/mall/orders/confirm-receipt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains("当前履约状态不可确认收货"));
    }

    private OrderSeed createOrderSeed(String buyerUsername, String payStatus, String fulfillStatus) {
        UserAccountEntity buyer = userAccountRepository.findByUsername(buyerUsername)
                .orElseThrow(() -> new IllegalArgumentException("buyer not found: " + buyerUsername));

        ProductEntity product = new ProductEntity();
        product.setSnCode("SN-RCV-" + UUID.randomUUID().toString().substring(0, 8));
        product.setBrand("RecvBrand");
        product.setModel("RecvModel");
        product.setProductionDate(LocalDate.of(2024, 1, 1));
        product.setImageUrl("https://example.com/recv.jpg");
        product.setWearScore(95);
        product.setRecycleGrade("GOOD");
        product.setEstimatedRecyclePrice(new BigDecimal("900.00"));
        productRepository.save(product);

        RecycleOrderEntity recycleOrder = new RecycleOrderEntity();
        recycleOrder.setOrderNo("RCY-RCV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        recycleOrder.setUser(buyer);
        recycleOrder.setProduct(product);
        recycleOrder.setEstimatedPrice(new BigDecimal("900.00"));
        recycleOrder.setGrade("GOOD");
        recycleOrder.setStatus("LISTED");
        recycleOrder.setCreatedAt(LocalDateTime.now());
        recycleOrderRepository.save(recycleOrder);

        ResaleListingEntity listing = new ResaleListingEntity();
        listing.setRecycleOrder(recycleOrder);
        listing.setProduct(product);
        listing.setSalePrice(new BigDecimal("1080.00"));
        listing.setStock(1);
        listing.setStatus("ON_SHELF");
        listing.setCreatedAt(LocalDateTime.now());
        resaleListingRepository.save(listing);

        ResaleOrderEntity order = new ResaleOrderEntity();
        String orderNo = "B2C-RCV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setOrderNo(orderNo);
        order.setBuyerUser(buyer);
        order.setListing(listing);
        order.setAmount(new BigDecimal("1080.00"));
        order.setPayStatus(payStatus);
        order.setFulfillStatus(fulfillStatus);
        order.setCreatedAt(LocalDateTime.now());
        resaleOrderRepository.save(order);
        return new OrderSeed(orderNo, buyer.getId());
    }

    private record OrderSeed(String orderNo, Long buyerUserId) {
    }
}
