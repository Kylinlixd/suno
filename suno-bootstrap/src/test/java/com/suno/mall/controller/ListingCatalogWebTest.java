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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MKT-020 搜索/分页、MKT-038 单品详情、MKT-039 分类目录 Web 测试。
 * 数据经真实服务链路构造：注册用户 → 回收单三步审核 → LISTED → 发布二销 listing。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListingCatalogWebTest {

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
                                new AuthController.RegisterRequest("catalog_" + suffix, "Str0ngPass!2026", "web-test"))))
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

    /** 推进一个回收单到 LISTED 并发布二销商品，返回 listingId。 */
    private Long publishListingFor(String accessToken, String snSuffix, int stock) throws Exception {
        Long userId = currentUserId(accessToken);
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-CATALOG-" + snSuffix, "https://cdn.suno.test/" + snSuffix + ".jpg", 90, 0);
        String orderNo = (String) recycle.get("orderNo");
        recycleApplicationService.transitionOrder(orderNo, "QUALITY_CHECK", null);
        recycleApplicationService.transitionOrder(orderNo, "PRICE_REVIEW", null);
        recycleApplicationService.transitionOrder(orderNo, "LIST_ON_SHELF", null);
        Map<String, Object> listing = recycleApplicationService.publishResaleListing(
                orderNo, new BigDecimal("2199.00"), stock);
        return ((Number) listing.get("listingId")).longValue();
    }

    @Test
    void listsEnabledCategoriesInSortOrder() throws Exception {
        String token = registerAndLogin("cat_" + System.nanoTime());
        MvcResult result = mockMvc.perform(get("/api/mall/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode categories = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(categories.isArray() && categories.size() >= 6, "V2001 迁移应至少种子 6 个启用分类");
        int previousSort = Integer.MIN_VALUE;
        List<String> names = new java.util.ArrayList<>();
        for (JsonNode category : categories) {
            int sortOrder = category.get("sortOrder").asInt();
            assertTrue(sortOrder > previousSort, "分类应按 sortOrder 升序");
            previousSort = sortOrder;
            names.add(category.get("name").asText());
        }
        assertTrue(names.contains("手机") && names.contains("数码配件"));
    }

    @Test
    void filtersListingsByKeywordCategoryAndGrade() throws Exception {
        String token = registerAndLogin("filter_" + System.nanoTime());
        Long listingId = publishListingFor(token, "F" + System.nanoTime(), 3);

        // SN 关键词 + 成色可组合过滤（测试环境无估价规则，SN 桩 24 个月 → 兜底 MEDIUM）
        MvcResult bySn = mockMvc.perform(get("/api/mall/listings")
                        .param("keyword", "SN-CATALOG-")
                        .param("grade", "MEDIUM")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode snPage = objectMapper.readTree(bySn.getResponse().getContentAsString()).get("data");
        assertTrue(snPage.get("content").size() >= 1);
        assertTrue(containsListing(snPage.get("content"), listingId));

        // 分类过滤：测试商品回落到首个启用分类（手机 id=1）
        MvcResult byCategory = mockMvc.perform(get("/api/mall/listings")
                        .param("categoryId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(containsListing(objectMapper.readTree(byCategory.getResponse().getContentAsString()).at("/data/content"), listingId));

        // 命中不了的关键词返回空页
        MvcResult empty = mockMvc.perform(get("/api/mall/listings")
                        .param("keyword", "no-such-keyword-" + System.nanoTime())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, objectMapper.readTree(empty.getResponse().getContentAsString()).at("/data/content").size());

        // 未知分类返回稳定错误码
        MvcResult unknownCategory = mockMvc.perform(get("/api/mall/listings")
                        .param("categoryId", "999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("CATEGORY_NOT_FOUND",
                objectMapper.readTree(unknownCategory.getResponse().getContentAsString()).get("errorCode").asText());
    }

    @Test
    void paginatesListingSearchWithZeroBasedPages() throws Exception {
        String token = registerAndLogin("page_" + System.nanoTime());
        publishListingFor(token, "P1" + System.nanoTime(), 1);
        publishListingFor(token, "P2" + System.nanoTime(), 1);
        publishListingFor(token, "P3" + System.nanoTime(), 1);

        MvcResult page0 = mockMvc.perform(get("/api/mall/listings")
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(page0.getResponse().getContentAsString()).get("data");
        assertEquals(0, data.get("page").asInt());
        assertEquals(2, data.get("size").asInt());
        assertEquals(2, data.get("content").size());
        assertTrue(data.get("totalElements").asLong() >= 3);
        assertEquals((data.get("totalElements").asLong() + 1) / 2, data.get("totalPages").asLong());

        long lastPage = data.get("totalPages").asLong() - 1;
        MvcResult pageLast = mockMvc.perform(get("/api/mall/listings")
                        .param("page", String.valueOf(lastPage)).param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode lastData = objectMapper.readTree(pageLast.getResponse().getContentAsString()).get("data");
        assertFalse(lastData.get("content").size() > 2);
    }

    @Test
    void returnsListingDetailWithReviewSummary() throws Exception {
        String token = registerAndLogin("detail_" + System.nanoTime());
        Long listingId = publishListingFor(token, "D" + System.nanoTime(), 2);

        MvcResult result = mockMvc.perform(get("/api/mall/listings/" + listingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(listingId.longValue(), detail.get("listingId").asLong());
        assertEquals("ON_SHELF", detail.get("status").asText());
        assertEquals(2199.00, detail.get("salePrice").asDouble());
        assertEquals(2, detail.get("stock").asInt());
        assertEquals(0, detail.get("reviewCount").asInt());
        assertTrue(detail.has("image") && !detail.get("image").asText().isBlank());
        assertTrue(detail.has("categoryName"), "详情应携带分类名");
    }

    @Test
    void rejectsUnknownListingDetail() throws Exception {
        String token = registerAndLogin("miss_" + System.nanoTime());
        MvcResult result = mockMvc.perform(get("/api/mall/listings/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("LISTING_NOT_FOUND",
                objectMapper.readTree(result.getResponse().getContentAsString()).get("errorCode").asText());
    }

    /** 生产级商城语义：浏览（列表/详情/分类）匿名可读，登录只在交易动作时要求 */
    @Test
    void anonymousCanBrowseCatalog() throws Exception {
        mockMvc.perform(get("/api/mall/listings").param("page", "0").param("size", "5"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/mall/categories"))
                .andExpect(status().isOk());
        MvcResult detail = mockMvc.perform(get("/api/mall/listings/1"))
                .andReturn();
        // 匿名可访问；1 号商品可能不存在，但必须不是 401
        assertTrue(detail.getResponse().getStatus() != 401, "单品详情不应要求登录");
    }

    private static boolean containsListing(JsonNode content, Long listingId) {
        for (JsonNode item : content) {
            if (item.get("listingId").asLong() == listingId) {
                return true;
            }
        }
        return false;
    }
}
