package com.suno.mall.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.controller.CouponExpireReminderScheduler;
import com.suno.mall.dao.CouponRepository;
import com.suno.mall.dao.CouponUserRepository;
import com.suno.mall.entity.CouponEntity;
import com.suno.mall.entity.CouponUserEntity;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.dao.UserAccountRepository;
import com.suno.mall.service.RecycleApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1 增长功能 Web 测试：短信登录（IDN-009/010）、部分退款（MKT-122/123）、
 * 折扣券、积分抵现、图片上传与图库（MKT-070/071）、券到期提醒（MKT-S003）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrowthFeaturesWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecycleApplicationService recycleApplicationService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUserRepository couponUserRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CouponExpireReminderScheduler couponExpireReminderScheduler;

    private String registerAndLogin(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest("grow_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private Long currentUserId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();
    }

    private String adminToken(String suffix) throws Exception {
        String username = "growadm_" + suffix;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthController.RegisterRequest(username, "Str0ngPass!2026", "web-test"))));
        UserAccountEntity user = userAccountRepository.findByUsername(username).orElseThrow();
        user.setRoleCode("ADMIN");
        userAccountRepository.save(user);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest(username, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    /** 回收三审 → 上架，返回 listingId */
    private Long publishListing(String token, String snSuffix, BigDecimal price) throws Exception {
        Long userId = currentUserId(token);
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-GROW-" + snSuffix, "https://cdn.suno.test/grow.jpg", 90, 0);
        String recycleOrderNo = (String) recycle.get("orderNo");
        recycleApplicationService.transitionOrder(recycleOrderNo, "QUALITY_CHECK", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "PRICE_REVIEW", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "LIST_ON_SHELF", null);
        return ((Number) recycleApplicationService.publishResaleListing(recycleOrderNo, price, 3)
                .get("listingId")).longValue();
    }

    @Test
    void smsCodeLoginConsumesCodeAndAutoRegisters() throws Exception {
        String phone = "139" + String.valueOf(System.nanoTime()).substring(0, 8);
        MvcResult send = mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.SmsSendRequest(phone))))
                .andExpect(status().isOk())
                .andReturn();
        String code = objectMapper.readTree(send.getResponse().getContentAsString()).at("/data/devCode").asText();
        assertEquals(6, code.length(), "测试环境 dev-echo 应回显验证码");

        mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.SmsLoginRequest(phone, code, "web-test"))))
                .andExpect(status().isOk());

        // 同一验证码二次使用必须拒绝
        mockMvc.perform(post("/api/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.SmsLoginRequest(phone, code, "web-test"))))
                .andExpect(status().isBadRequest());

        // 非法手机号
        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.SmsSendRequest("12345"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void partialRefundKeepsOrderPaidWhileFullRefundClosesIt() throws Exception {
        long nonce = System.nanoTime();
        String buyer = registerAndLogin("pr_" + nonce);
        String admin = adminToken("pr_" + nonce);
        Long listingId = publishListing(buyer, "P" + nonce, new BigDecimal("1599.00"));

        MvcResult order = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingId))))
                .andExpect(status().isOk()).andReturn();
        JsonNode orderData = objectMapper.readTree(order.getResponse().getContentAsString()).get("data");
        String orderNo = orderData.get("orderNo").asText();
        recycleApplicationService.markResaleOrderPaidWithIdempotency(orderNo, "pay-grow-" + nonce);

        // 部分退款 100
        MvcResult apply = mockMvc.perform(post("/api/mall/orders/" + orderNo + "/refund-apply")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "外包装轻微破损协商补偿", "amount", 100))))
                .andExpect(status().isOk()).andReturn();
        long refundId = extractRefundId(buyer);
        assertEquals(100.00, objectMapper.readTree(apply.getResponse().getContentAsString()).at("/data/amount").asDouble());

        MvcResult process = mockMvc.perform(post("/api/admin/recycle/refunds/" + refundId + "/process")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        assertEquals("PARTIAL", objectMapper.readTree(process.getResponse().getContentAsString()).at("/data/mode").asText());
        assertEquals("PAID", recycleApplicationService.getOrderAmountForGateway(orderNo).get("payStatus"));

        // 管理端列表可见
        MvcResult adminList = mockMvc.perform(get("/api/admin/recycle/refunds?status=PROCESSED").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        assertTrue(objectMapper.readTree(adminList.getResponse().getContentAsString()).at("/data/totalElements").asLong() >= 1);

        // 全额退款走整单关闭
        mockMvc.perform(post("/api/mall/orders/" + orderNo + "/refund-apply")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "商品质量问题申请全额退款"))))
                .andExpect(status().isOk());
        long fullRefundId = extractRefundId(buyer);
        MvcResult full = mockMvc.perform(post("/api/admin/recycle/refunds/" + fullRefundId + "/process")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        assertEquals("FULL", objectMapper.readTree(full.getResponse().getContentAsString()).at("/data/mode").asText());
        assertEquals("REFUNDED", recycleApplicationService.getOrderAmountForGateway(orderNo).get("payStatus"));
    }

    @Test
    void percentCouponAndPointsDeductionReducePayable() throws Exception {
        long nonce = System.nanoTime();
        String buyer = registerAndLogin("pp_" + nonce);
        Long userId = currentUserId(buyer);
        String admin = adminToken("pp_" + nonce);
        Long listingId = publishListing(buyer, "C" + nonce, new BigDecimal("1000.00"));

        // 折扣券 10%
        MvcResult created = mockMvc.perform(post("/api/admin/coupons").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminCouponController.CreateCouponRequest(
                                "九折券", "PERCENT", BigDecimal.ZERO, new BigDecimal("10"), 50, 1, 30))))
                .andExpect(status().isOk()).andReturn();
        long couponId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/couponId").asLong();
        MvcResult claim = mockMvc.perform(post("/api/mall/coupons/" + couponId + "/claim")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk()).andReturn();
        long couponUserId = objectMapper.readTree(claim.getResponse().getContentAsString()).at("/data/couponUserId").asLong();

        // 给账户充值积分（模拟回收所得）：直接调积分入账路径
        UserAccountEntity user = userAccountRepository.findById(userId).orElseThrow();
        user.setPoints(5000);
        userAccountRepository.save(user);

        MvcResult order = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "listingId", listingId, "couponUserId", couponUserId, "usePoints", true))))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(order.getResponse().getContentAsString()).get("data");
        assertEquals(100.00, data.get("discountAmount").asDouble(), "10% 折扣券抵 100");
        // 应付 900 → 积分最多抵 20% = 180 → 5000 积分足够，消耗 18000？不：180 元需 18000 积分 > 5000 → 受余额限制抵 50 元
        assertEquals(50.00, data.get("pointsDeductAmount").asDouble(), "积分抵扣受余额限制（5000 分 = 50 元）");
        assertEquals(850.00, data.get("payableAmount").asDouble());
        assertEquals(5000, data.get("pointsUsed").asInt());

        // 取消订单：积分回退
        String orderNo = data.get("orderNo").asText();
        mockMvc.perform(post("/api/mall/orders/cancel").header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderNo", orderNo))))
                .andExpect(status().isOk());
        UserAccountEntity afterCancel = userAccountRepository.findById(userId).orElseThrow();
        assertEquals(5000, afterCancel.getPoints().intValue(), "取消订单应全额回退积分");
    }

    @Test
    void uploadServesFileAndGalleryAttachedToDetail() throws Exception {
        long nonce = System.nanoTime();
        String buyer = registerAndLogin("up_" + nonce);
        String admin = adminToken("up_" + nonce);
        Long listingId = publishListing(buyer, "U" + nonce, new BigDecimal("899.00"));

        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        MvcResult upload = mockMvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", png))
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk())
                .andReturn();
        String url = objectMapper.readTree(upload.getResponse().getContentAsString()).at("/data/url").asText();
        assertTrue(url.startsWith("/files/"));

        mockMvc.perform(get(url)).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/recycle/listings/" + listingId + "/images")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminCatalogController.AttachImageRequest(url))))
                .andExpect(status().isOk());

        MvcResult detail = mockMvc.perform(get("/api/mall/listings/" + listingId))
                .andExpect(status().isOk()).andReturn();
        JsonNode images = objectMapper.readTree(detail.getResponse().getContentAsString()).at("/data/images");
        assertTrue(images.isArray() && images.size() >= 2, "详情图库应包含主图与追加图");

        // 拒绝非白名单扩展
        mockMvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("file", "evil.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void couponExpireReminderPushesOnce() throws Exception {
        long nonce = System.nanoTime();
        String buyer = registerAndLogin("ex_" + nonce);
        Long userId = currentUserId(buyer);
        CouponEntity coupon = new CouponEntity();
        coupon.setName("将到期券");
        coupon.setCouponType("FIXED");
        coupon.setDiscountValue(new BigDecimal("50"));
        coupon.setMinOrderAmount(new BigDecimal("100"));
        coupon.setTotalCount(10);
        coupon.setRemainingCount(10);
        coupon.setPerUserLimit(1);
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(2));
        coupon.setStatus("ACTIVE");
        coupon = couponRepository.save(coupon);
        CouponUserEntity mine = new CouponUserEntity();
        mine.setUser(userAccountRepository.findById(userId).orElseThrow());
        mine.setCoupon(coupon);
        mine.setStatus("UNUSED");
        mine.setExpireAt(LocalDateTime.now().plusDays(2));
        couponUserRepository.save(mine);

        int first = couponExpireReminderScheduler.remindExpiringCoupons();
        assertTrue(first >= 1);
        int second = couponExpireReminderScheduler.remindExpiringCoupons();
        CouponUserEntity stored = couponUserRepository.findById(mine.getId()).orElseThrow();
        assertTrue(stored.getExpireRemindedAt() != null, "提醒后应落标记");
        // 幂等：第二次扫描的提醒量必须小于第一次（已提醒的券被标记跳过）
        assertTrue(second < first, "第二次扫描应跳过已提醒的券，second=" + second + " first=" + first);
    }

    private long extractRefundId(String token) throws Exception {
        MvcResult list = mockMvc.perform(get("/api/mall/refunds").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = objectMapper.readTree(list.getResponse().getContentAsString()).at("/data/content");
        return content.get(0).get("refundId").asLong();
    }
}
