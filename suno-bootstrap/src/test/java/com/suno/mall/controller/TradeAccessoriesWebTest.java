package com.suno.mall.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.dao.CouponRepository;
import com.suno.mall.entity.CouponEntity;
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
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2 购物车（MKT-040~042）与优惠券（MKT-050~052）及下单集成（MKT-024 扩展）Web 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeAccessoriesWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecycleApplicationService recycleApplicationService;

    @Autowired
    private CouponRepository couponRepository;

    private String registerAndLogin(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest("trade_" + suffix, "Str0ngPass!2026", "web-test"))))
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

    private Long publishListing(String token, String snSuffix) throws Exception {
        Long userId = currentUserId(token);
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-TRADE-" + snSuffix, "https://cdn.suno.test/" + snSuffix + ".jpg", 90, 0);
        String orderNo = (String) recycle.get("orderNo");
        recycleApplicationService.transitionOrder(orderNo, "QUALITY_CHECK", null);
        recycleApplicationService.transitionOrder(orderNo, "PRICE_REVIEW", null);
        recycleApplicationService.transitionOrder(orderNo, "LIST_ON_SHELF", null);
        return ((Number) recycleApplicationService.publishResaleListing(orderNo, new BigDecimal("2199.00"), 3)
                .get("listingId")).longValue();
    }

    private CouponEntity seedCoupon(String name, BigDecimal minOrderAmount, BigDecimal discount, int perUserLimit) {
        CouponEntity coupon = new CouponEntity();
        coupon.setName(name);
        coupon.setCouponType("FIXED");
        coupon.setDiscountValue(discount);
        coupon.setMinOrderAmount(minOrderAmount);
        coupon.setTotalCount(100);
        coupon.setRemainingCount(100);
        coupon.setPerUserLimit(perUserLimit);
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(30));
        coupon.setStatus("ACTIVE");
        return couponRepository.save(coupon);
    }

    @Test
    void cartRoundTripIsIdempotentPerListing() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("cart_" + nonce);
        Long listingId = publishListing(token, "C" + nonce);

        MvcResult add = mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartController.AddCartItemRequest(listingId))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode addBody = objectMapper.readTree(add.getResponse().getContentAsString()).get("data");
        assertTrue(addBody.get("idempotentReplay").asBoolean() == false);
        long cartItemId = addBody.get("cartItemId").asLong();

        // 重复加购幂等
        MvcResult again = mockMvc.perform(post("/api/cart").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartController.AddCartItemRequest(listingId))))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(again.getResponse().getContentAsString()).at("/data/idempotentReplay").asBoolean());

        MvcResult list = mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(list.getResponse().getContentAsString()).at("/data/content");
        assertEquals(1, content.size());
        assertTrue(content.get(0).get("orderable").asBoolean());

        mockMvc.perform(delete("/api/cart/" + cartItemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        MvcResult afterRemove = mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, objectMapper.readTree(afterRemove.getResponse().getContentAsString()).at("/data/content").size());
    }

    @Test
    void couponClaimAppliesPerUserLimitAndRedeemsOnOrder() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("coupon_" + nonce);
        Long userId = currentUserId(token);
        Long listingId = publishListing(token, "K" + nonce);
        CouponEntity coupon = seedCoupon("满2000减200", new BigDecimal("2000"), new BigDecimal("200"), 1);

        mockMvc.perform(get("/api/mall/coupons").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        MvcResult claim = mockMvc.perform(post("/api/mall/coupons/" + coupon.getId() + "/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long couponUserId = objectMapper.readTree(claim.getResponse().getContentAsString()).at("/data/couponUserId").asLong();

        // 超单人上限
        MvcResult overLimit = mockMvc.perform(post("/api/mall/coupons/" + coupon.getId() + "/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("COUPON_CLAIM_LIMIT",
                objectMapper.readTree(overLimit.getResponse().getContentAsString()).get("errorCode").asText());

        // 我的券
        MvcResult mine = mockMvc.perform(get("/api/mall/coupons/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, objectMapper.readTree(mine.getResponse().getContentAsString()).at("/data/totalElements").asLong());

        // 建地址并用券下单：2199 - 200 = 1999
        MvcResult address = mockMvc.perform(post("/api/addresses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressController.AddressRequest(
                                "张三", "13800138000", "上海市", "上海市", "浦东新区", "世纪大道 100 号", true))))
                .andExpect(status().isOk())
                .andReturn();
        long addressId = objectMapper.readTree(address.getResponse().getContentAsString()).at("/data/addressId").asLong();

        MvcResult order = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "listingId", listingId, "addressId", addressId, "couponUserId", couponUserId))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode orderData = objectMapper.readTree(order.getResponse().getContentAsString()).get("data");
        assertEquals(2199.00, orderData.get("amount").asDouble());
        assertEquals(200.00, orderData.get("discountAmount").asDouble());
        assertEquals(1999.00, orderData.get("payableAmount").asDouble());
        String orderNo = orderData.get("orderNo").asText();

        // 下单后购物车应移除该商品、券变 USED
        MvcResult mineUsed = mockMvc.perform(get("/api/mall/coupons/mine?status=USED").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, objectMapper.readTree(mineUsed.getResponse().getContentAsString()).at("/data/totalElements").asLong());

        // 取消订单：券回滚为 UNUSED，可再次使用
        mockMvc.perform(post("/api/mall/orders/cancel").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderNo", orderNo))))
                .andExpect(status().isOk());
        MvcResult mineUnused = mockMvc.perform(get("/api/mall/coupons/mine?status=UNUSED").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, objectMapper.readTree(mineUnused.getResponse().getContentAsString()).at("/data/totalElements").asLong());
    }

    @Test
    void rejectsCouponBelowThresholdAndRollsBackOrder() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("threshold_" + nonce);
        Long listingId = publishListing(token, "T" + nonce);
        CouponEntity coupon = seedCoupon("满9999减500", new BigDecimal("9999"), new BigDecimal("500"), 1);
        MvcResult claim = mockMvc.perform(post("/api/mall/coupons/" + coupon.getId() + "/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long couponUserId = objectMapper.readTree(claim.getResponse().getContentAsString()).at("/data/couponUserId").asLong();

        MvcResult rejected = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "listingId", listingId, "couponUserId", couponUserId))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("COUPON_MIN_AMOUNT_NOT_MET",
                objectMapper.readTree(rejected.getResponse().getContentAsString()).get("errorCode").asText());

        // 下单回滚后券仍为 UNUSED，商品仍在售（库存未占用）
        MvcResult mineUnused = mockMvc.perform(get("/api/mall/coupons/mine?status=UNUSED").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, objectMapper.readTree(mineUnused.getResponse().getContentAsString()).at("/data/totalElements").asLong());
        MvcResult detail = mockMvc.perform(get("/api/mall/listings/" + listingId))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(3, objectMapper.readTree(detail.getResponse().getContentAsString()).at("/data/stock").asInt());
    }

    @Test
    void orderWithoutAddressKeepsLegacyCompatibility() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("legacy_" + nonce);
        Long listingId = publishListing(token, "L" + nonce);
        mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingId))))
                .andExpect(status().isOk());
    }
}
