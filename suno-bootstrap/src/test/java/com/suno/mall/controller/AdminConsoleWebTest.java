package com.suno.mall.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.dao.UserAccountRepository;
import com.suno.mall.entity.UserAccountEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 运营后台 Web 测试：商品/订单/用户/优惠券/报表（MKT-104~107/120~121/130~132、IDN-110~111、OPS-060）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminConsoleWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecycleApplicationService recycleApplicationService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private String register(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest("admin_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/username").asText();
    }

    private String loginAs(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.LoginRequest(username, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    /** 注册普通用户并提升为 ADMIN（重新登录获取携带 ADMIN 角色的 JWT） */
    private String adminToken(String suffix) throws Exception {
        String username = register(suffix);
        UserAccountEntity user = userAccountRepository.findByUsername(username).orElseThrow();
        user.setRoleCode("ADMIN");
        userAccountRepository.save(user);
        return loginAs(username);
    }

    private Long publishListing(String buyerToken, String snSuffix) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk()).andReturn();
        long userId = objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-ADMIN-" + snSuffix, "https://cdn.suno.test/admin.jpg", 90, 0);
        String recycleOrderNo = (String) recycle.get("orderNo");
        recycleApplicationService.transitionOrder(recycleOrderNo, "QUALITY_CHECK", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "PRICE_REVIEW", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "LIST_ON_SHELF", null);
        return ((Number) recycleApplicationService.publishResaleListing(recycleOrderNo, new BigDecimal("2599.00"), 2)
                .get("listingId")).longValue();
    }

    @Test
    void managesListingsPriceAndShelf() throws Exception {
        long nonce = System.nanoTime();
        String buyerToken = loginAs(register("buyer_" + nonce));
        String admin = adminToken("list_" + nonce);
        Long listingId = publishListing(buyerToken, "L" + nonce);

        MvcResult page = mockMvc.perform(get("/api/admin/recycle/listings").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        assertTrue(objectMapper.readTree(page.getResponse().getContentAsString()).at("/data/totalElements").asLong() >= 1);

        MvcResult price = mockMvc.perform(post("/api/admin/recycle/listings/" + listingId + "/price")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminCatalogController.AdjustPriceRequest(new BigDecimal("2399.00")))))
                .andExpect(status().isOk()).andReturn();
        assertEquals(2399.00, objectMapper.readTree(price.getResponse().getContentAsString()).at("/data/salePrice").asDouble());

        mockMvc.perform(post("/api/admin/recycle/listings/" + listingId + "/off-shelf").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/recycle/listings/" + listingId + "/on-shelf").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        // 普通用户无权访问
        mockMvc.perform(get("/api/admin/recycle/listings").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void pagesOrdersAndShowsDetailWithReceiver() throws Exception {
        long nonce = System.nanoTime();
        String buyerToken = loginAs(register("buyer_" + nonce));
        String admin = adminToken("ord_" + nonce);
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk()).andReturn();
        long userId = objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();

        MvcResult address = mockMvc.perform(post("/api/addresses").header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressController.AddressRequest(
                                "张三", "13800138000", "上海市", "上海市", "浦东新区", "世纪大道 100 号", true))))
                .andExpect(status().isOk()).andReturn();
        long addressId = objectMapper.readTree(address.getResponse().getContentAsString()).at("/data/addressId").asLong();
        Long listingId = publishListing(buyerToken, "O" + nonce);
        MvcResult order = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingId, "addressId", addressId))))
                .andExpect(status().isOk()).andReturn();
        String orderNo = objectMapper.readTree(order.getResponse().getContentAsString()).at("/data/orderNo").asText();

        MvcResult pageOrders = mockMvc.perform(get("/api/admin/recycle/resale-orders")
                        .param("keyword", orderNo)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = objectMapper.readTree(pageOrders.getResponse().getContentAsString()).at("/data/content");
        assertEquals(1, content.size());

        MvcResult detail = mockMvc.perform(get("/api/admin/recycle/resale-orders/" + orderNo)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode detailData = objectMapper.readTree(detail.getResponse().getContentAsString()).get("data");
        assertEquals("张三", detailData.get("receiverName").asText());
        assertTrue(detailData.get("receiverAddress").asText().contains("世纪大道"));
        assertEquals(userId, detailData.get("buyerUserId").asLong());
    }

    @Test
    void bansUserAndBlocksLogin() throws Exception {
        long nonce = System.nanoTime();
        String target = register("target_" + nonce);
        String admin = adminToken("ban_" + nonce);
        MvcResult me = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest(target, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk()).andReturn();
        String targetToken = objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/accessToken").asText();
        MvcResult targetMe = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk()).andReturn();
        long targetUserId = objectMapper.readTree(targetMe.getResponse().getContentAsString()).at("/data/userId").asLong();

        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminUserController.SetStatusRequest("BANNED"))))
                .andExpect(status().isOk());
        MvcResult bannedLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginRequest(target, "Str0ngPass!2026", "web-test"))))
                .andReturn();
        assertNotEquals(200, bannedLogin.getResponse().getStatus(), "封禁后登录必须失败");

        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminUserController.SetStatusRequest("ACTIVE"))))
                .andExpect(status().isOk());
    }

    @Test
    void managesCouponLifecycle() throws Exception {
        long nonce = System.nanoTime();
        String admin = adminToken("cpn_" + nonce);
        MvcResult created = mockMvc.perform(post("/api/admin/coupons").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminCouponController.CreateCouponRequest(
                                "运营满减券", "FIXED", new BigDecimal("1000"), new BigDecimal("100"), 50, 1, 30))))
                .andExpect(status().isOk()).andReturn();
        long couponId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/couponId").asLong();

        MvcResult list = mockMvc.perform(get("/api/admin/coupons?status=ACTIVE").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        assertTrue(objectMapper.readTree(list.getResponse().getContentAsString()).get("data").size() >= 1);

        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/disable").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void reportOverviewAggregatesAndTrends() throws Exception {
        long nonce = System.nanoTime();
        String admin = adminToken("rpt_" + nonce);
        MvcResult overview = mockMvc.perform(get("/api/admin/reports/overview").param("trendDays", "7")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(overview.getResponse().getContentAsString()).get("data");
        assertTrue(data.get("totalOrders").asLong() >= 0);
        assertTrue(data.get("gmv").isNumber());
        assertTrue(data.get("trend").isArray() && data.get("trend").size() == 7, "趋势应包含 7 天数据点");
    }
}
