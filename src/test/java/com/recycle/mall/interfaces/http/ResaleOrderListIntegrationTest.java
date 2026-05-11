package com.recycle.mall.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.recycle.mall.common.CacheContract;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResaleOrderListIntegrationTest extends TestAuthSupport {

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
    void shouldListBuyerOrdersByCreatedAtDescAndSupportLimit() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        String oldestOrder = createOrderSeed(buyer, "PAID", "TO_DELIVER", 90);
        String middleOrder = createOrderSeed(buyer, "PAID", "DELIVERED", 60);
        String newestOrder = createOrderSeed(buyer, "REFUNDED", "REFUNDED", 30);

        MvcResult result = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CacheContract.HEADER_CACHE_DIGEST))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertTrue(data.has(CacheContract.FIELD_CACHE_DIGEST));
        assertEquals(2, data.get("total").asInt());
        JsonNode items = data.get("items");
        assertEquals(2, items.size());
        assertEquals(newestOrder, items.get(0).get("orderNo").asText());
        assertEquals(middleOrder, items.get(1).get("orderNo").asText());
        assertNotEquals(oldestOrder, items.get(0).get("orderNo").asText());
    }

    @Test
    void shouldFilterBuyerOrdersByPayStatusAndFulfillStatus() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        createOrderSeed(buyer, "PAID", "DELIVERED", 80);
        createOrderSeed(buyer, "PAID", "TO_DELIVER", 70);
        String expected = createOrderSeed(buyer, "PAID", "DELIVERED", 50);

        MvcResult result = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("payStatus", "paid")
                        .param("fulfillStatus", "delivered")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertTrue(data.get("total").asInt() >= 1);
        JsonNode items = data.get("items");
        boolean foundExpected = false;
        for (JsonNode item : items) {
            assertEquals("PAID", item.get("payStatus").asText());
            assertEquals("DELIVERED", item.get("fulfillStatus").asText());
            if (expected.equals(item.get("orderNo").asText())) {
                foundExpected = true;
            }
        }
        assertTrue(foundExpected);
    }

    @Test
    void shouldSupportPageAndSizeForBuyerOrderList() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        String first = createOrderSeed(buyer, "PAID", "PAGE_TEST_ONLY", 95);
        String second = createOrderSeed(buyer, "PAID", "PAGE_TEST_ONLY", 85);
        String third = createOrderSeed(buyer, "PAID", "PAGE_TEST_ONLY", 75);

        MvcResult result = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("payStatus", "PAID")
                        .param("fulfillStatus", "PAGE_TEST_ONLY")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertEquals(1, data.get("page").asInt());
        assertEquals(2, data.get("size").asInt());
        assertEquals(3, data.get("totalMatched").asInt());
        JsonNode items = data.get("items");
        assertEquals(1, items.size());
        assertEquals(first, items.get(0).get("orderNo").asText());
        assertTrue(data.get("hasMore").isBoolean());
        assertFalse(data.get("hasMore").asBoolean());
        assertNotEquals(third, items.get(0).get("orderNo").asText());
        assertNotEquals(second, items.get(0).get("orderNo").asText());
    }

    @Test
    void shouldSupportSortOrderAscForBuyerOrderList() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        String oldest = createOrderSeed(buyer, "PAID", "SORT_TEST_ONLY", 120);
        String newest = createOrderSeed(buyer, "PAID", "SORT_TEST_ONLY", 20);

        MvcResult result = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("payStatus", "PAID")
                        .param("fulfillStatus", "SORT_TEST_ONLY")
                        .param("sortBy", "createdAt")
                        .param("sortOrder", "asc")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        JsonNode items = data.get("items");
        assertEquals(2, items.size());
        assertEquals(oldest, items.get(0).get("orderNo").asText());
        assertEquals(newest, items.get(1).get("orderNo").asText());
        assertEquals("createdAt", data.get("filters").get("sortBy").asText());
        assertEquals("asc", data.get("filters").get("sortOrder").asText());
    }

    @Test
    void shouldSupportSortByAmountDescForBuyerOrderList() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        String lowAmountOrder = createOrderSeed(buyer, "PAID", "AMOUNT_SORT_TEST_ONLY", 120, new BigDecimal("120.00"));
        String midAmountOrder = createOrderSeed(buyer, "PAID", "AMOUNT_SORT_TEST_ONLY", 80, new BigDecimal("260.00"));
        String highAmountOrder = createOrderSeed(buyer, "PAID", "AMOUNT_SORT_TEST_ONLY", 40, new BigDecimal("520.00"));

        MvcResult result = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("payStatus", "PAID")
                        .param("fulfillStatus", "AMOUNT_SORT_TEST_ONLY")
                        .param("sortBy", "amount")
                        .param("sortOrder", "desc")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        JsonNode items = data.get("items");
        assertEquals(3, items.size());
        assertEquals(highAmountOrder, items.get(0).get("orderNo").asText());
        assertEquals(midAmountOrder, items.get(1).get("orderNo").asText());
        assertEquals(lowAmountOrder, items.get(2).get("orderNo").asText());
        assertEquals("amount", data.get("filters").get("sortBy").asText());
        assertEquals("desc", data.get("filters").get("sortOrder").asText());
    }

    @Test
    void shouldFailWhenSortByIsUnsupported() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        createOrderSeed(buyer, "PAID", "INVALID_SORT_FIELD_TEST", 30);

        MvcResult result = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("sortBy", "unknownField")
                        .param("sortOrder", "desc")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains("排序字段仅支持 createdAt 或 amount"));
    }

    @Test
    void shouldFailWhenSortOrderIsUnsupported() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        createOrderSeed(buyer, "PAID", "INVALID_SORT_ORDER_TEST", 30);

        MvcResult result = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("sortBy", "createdAt")
                        .param("sortOrder", "sideways")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains("排序方向仅支持 asc 或 desc"));
    }

    @Test
    void shouldReturnStatusTextAndI18nForBuyerOrderList() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        String orderNo = createOrderSeed(buyer, "PAID", "DELIVERED", 25);

        MvcResult result = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("fulfillStatus", "DELIVERED")
                        .param("sortBy", "createdAt")
                        .param("sortOrder", "desc")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode items = root.get("data").get("items");
        boolean found = false;
        for (JsonNode item : items) {
            if (orderNo.equals(item.get("orderNo").asText())) {
                found = true;
                assertEquals("待收货", item.get("statusText").asText());
                assertEquals("待收货", item.get("statusTextI18n").get("zh-CN").asText());
                assertEquals("Awaiting Receipt", item.get("statusTextI18n").get("en-US").asText());
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void shouldReturnOrderStatusDictionary() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/mall/orders/status-dictionary"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CacheContract.HEADER_CACHE_DIGEST))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertTrue(data.has(CacheContract.FIELD_CACHE_DIGEST));
        assertEquals("1.0.0", data.get("schemaVersion").asText());
        JsonNode payStatus = data.get("payStatus");
        JsonNode fulfillStatus = data.get("fulfillStatus");
        assertTrue(payStatus.isArray());
        assertTrue(fulfillStatus.isArray());

        boolean hasPaid = false;
        for (JsonNode item : payStatus) {
            if ("PAID".equals(item.get("code").asText())) {
                hasPaid = true;
                assertEquals("已支付", item.get("label").asText());
                assertEquals("Paid", item.get("labelI18n").get("en-US").asText());
                break;
            }
        }
        assertTrue(hasPaid);
    }

    @Test
    void shouldReturn304ForOrderStatusDictionaryWhenIfNoneMatchMatches() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/mall/orders/status-dictionary"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(CacheContract.HEADER_CACHE_DIGEST))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        String digest = first.getResponse().getHeader(CacheContract.HEADER_CACHE_DIGEST);
        assertTrue(etag != null && !etag.isBlank());
        assertTrue(digest != null && !digest.isBlank());

        mockMvc.perform(get("/api/mall/orders/status-dictionary")
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(CacheContract.HEADER_CACHE_DIGEST, digest));
    }

    @Test
    void shouldReturn304ForBuyerOrderListWhenIfNoneMatchMatches() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        createOrderSeed(buyer, "PAID", "ETAG_LIST_TEST", 15);

        MvcResult first = mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("fulfillStatus", "ETAG_LIST_TEST")
                        .param("sortBy", "createdAt")
                        .param("sortOrder", "desc")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(CacheContract.HEADER_CACHE_DIGEST))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        String digest = first.getResponse().getHeader(CacheContract.HEADER_CACHE_DIGEST);
        assertTrue(etag != null && !etag.isBlank());
        assertTrue(digest != null && !digest.isBlank());

        mockMvc.perform(get("/api/mall/orders")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("fulfillStatus", "ETAG_LIST_TEST")
                        .param("sortBy", "createdAt")
                        .param("sortOrder", "desc")
                        .param("limit", "10")
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(CacheContract.HEADER_CACHE_DIGEST, digest));
    }

    @Test
    void shouldReturnBuyerOrderSummary() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        createOrderSeed(buyer, "PAID", "TO_DELIVER", 50, new BigDecimal("100.00"));
        createOrderSeed(buyer, "PAID", "DELIVERED", 40, new BigDecimal("200.00"));
        createOrderSeed(buyer, "REFUNDED", "REFUNDED", 30, new BigDecimal("300.00"));

        MvcResult result = mockMvc.perform(get("/api/mall/orders/summary")
                        .param("buyerUserId", String.valueOf(buyer.getId())))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(CacheContract.HEADER_CACHE_DIGEST))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertTrue(data.has(CacheContract.FIELD_CACHE_DIGEST));
        assertTrue(data.get("totalOrders").asInt() >= 3);
        assertTrue(data.get("payStatusCounts").has("PAID"));
        assertTrue(data.get("fulfillStatusCounts").has("DELIVERED"));
        assertTrue(data.get("payStatusBreakdown").isArray());
        assertTrue(data.get("fulfillStatusBreakdown").isArray());
        assertTrue(data.get("totalAmount").decimalValue().compareTo(new BigDecimal("600.00")) >= 0);
        assertTrue(data.get("refundedOrders").asInt() >= 1);
        assertTrue(data.get("paidAmount").decimalValue().compareTo(new BigDecimal("300.00")) >= 0);
        assertTrue(data.get("refundedAmount").decimalValue().compareTo(new BigDecimal("300.00")) >= 0);
        assertTrue(data.get("completedOrders").asInt() >= 0);
        assertTrue(data.get("completionRate").decimalValue().compareTo(new BigDecimal("0")) >= 0);
        assertTrue(data.get("refundRate").decimalValue().compareTo(new BigDecimal("0")) >= 0);
        assertTrue(data.get("healthScore").decimalValue().compareTo(new BigDecimal("0")) >= 0);
        assertTrue(data.get("healthScore").decimalValue().compareTo(new BigDecimal("100")) <= 0);
        assertTrue(data.has("healthLevel"));
        assertTrue(data.get("healthLevelI18n").has("zh-CN"));
        assertTrue(data.get("healthLevelI18n").has("en-US"));
        assertTrue(data.has("healthMeta"));
        assertTrue(data.has("summaryMeta"));
        assertTrue(data.get("summaryMeta").get("cards").isArray());
        assertTrue(data.get("summaryMeta").get("cards").size() >= 1);
        JsonNode firstCard = data.get("summaryMeta").get("cards").get(0);
        assertTrue(firstCard.has("displayOrder"));
        assertTrue(firstCard.has("componentHint"));
        assertTrue(data.get("summaryMeta").get("windowOptions").isArray());
        boolean selectedWindowFound = false;
        for (JsonNode option : data.get("summaryMeta").get("windowOptions")) {
            if (option.get("selected").asBoolean()) {
                selectedWindowFound = true;
                assertTrue(option.has("days"));
                assertTrue(option.get("labelI18n").has("en-US"));
            }
        }
        assertTrue(selectedWindowFound);
        assertTrue(data.get("healthInsights").isArray());
        assertTrue(data.get("healthInsights").size() >= 1);
        assertEquals("healthScore = completionRate * 0.7 + (100 - refundRate) * 0.3", data.get("healthMeta").get("formula").asText());
        assertTrue(data.get("healthMeta").get("weights").has("completionRate"));
        assertTrue(data.get("healthMeta").get("levelThresholds").isArray());
        JsonNode firstInsight = data.get("healthInsights").get(0);
        assertTrue(firstInsight.has("type"));
        assertTrue(firstInsight.has("titleI18n"));
        assertTrue(firstInsight.get("titleI18n").has("zh-CN"));
        assertTrue(firstInsight.get("suggestionI18n").has("en-US"));
        JsonNode payBreakdown = data.get("payStatusBreakdown");
        boolean paidFound = false;
        for (JsonNode item : payBreakdown) {
            if ("PAID".equals(item.get("status").asText())) {
                paidFound = true;
                assertTrue(item.get("count").asInt() >= 1);
                assertTrue(item.get("ratio").decimalValue().compareTo(new BigDecimal("0")) > 0);
                assertTrue(item.get("amount").decimalValue().compareTo(new BigDecimal("300.00")) >= 0);
                assertEquals("已支付", item.get("label").asText());
                assertEquals("Paid", item.get("labelI18n").get("en-US").asText());
                break;
            }
        }
        assertTrue(paidFound);
    }

    @Test
    void shouldReturn304ForBuyerOrderSummaryWhenIfNoneMatchMatches() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        createOrderSeed(buyer, "PAID", "SUMMARY_ETAG_TEST", 22, new BigDecimal("150.00"));

        MvcResult first = mockMvc.perform(get("/api/mall/orders/summary")
                        .param("buyerUserId", String.valueOf(buyer.getId())))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(CacheContract.HEADER_CACHE_DIGEST))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        String digest = first.getResponse().getHeader(CacheContract.HEADER_CACHE_DIGEST);
        assertTrue(etag != null && !etag.isBlank());
        assertTrue(digest != null && !digest.isBlank());

        mockMvc.perform(get("/api/mall/orders/summary")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(CacheContract.HEADER_CACHE_DIGEST, digest));
    }

    @Test
    void shouldSupportLookbackDaysForBuyerOrderSummary() throws Exception {
        UserAccountEntity buyer = userAccountRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalArgumentException("buyer not found"));
        createOrderSeed(buyer, "PAID", "SUMMARY_LOOKBACK_TEST", 10, new BigDecimal("100.00"));
        createOrderSeed(buyer, "PAID", "SUMMARY_LOOKBACK_TEST", 24 * 60 * 45, new BigDecimal("200.00"));

        MvcResult result = mockMvc.perform(get("/api/mall/orders/summary")
                        .param("buyerUserId", String.valueOf(buyer.getId()))
                        .param("lookbackDays", "7"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertEquals(7, data.get("summaryScope").get("lookbackDays").asInt());
        assertTrue(data.get("summaryScope").has("fromTime"));
        assertTrue(data.get("summaryScope").has("toTime"));
        boolean selected7Days = false;
        for (JsonNode option : data.get("summaryMeta").get("windowOptions")) {
            if (option.get("days").asInt() == 7 && option.get("selected").asBoolean()) {
                selected7Days = true;
                break;
            }
        }
        assertTrue(selected7Days);
        assertTrue(data.get("totalAmount").decimalValue().compareTo(new BigDecimal("100.00")) >= 0);
        assertTrue(data.get("totalAmount").decimalValue().compareTo(new BigDecimal("200.00")) < 0);
        assertTrue(data.get("comparison").get("available").asBoolean());
        assertTrue(data.get("comparison").get("previousWindow").has("totalOrders"));
        assertTrue(data.get("comparison").get("delta").has("completionRate"));
        assertTrue(data.get("comparison").get("deltaI18n").get("completionRate").has("zh-CN"));
        assertTrue(data.get("comparison").get("trend").has("healthScore"));
        assertTrue(data.get("comparison").get("trendI18n").get("healthScore").has("en-US"));
        assertTrue(data.get("comparison").get("comparisonMeta").get("metrics").isArray());
        assertTrue(data.get("comparison").get("comparisonMeta").get("trendSemantics").has("UP"));
        JsonNode firstMetricMeta = data.get("comparison").get("comparisonMeta").get("metrics").get(0);
        assertTrue(firstMetricMeta.has("displayOrder"));
        assertTrue(firstMetricMeta.has("chartHint"));
    }

    private String createOrderSeed(UserAccountEntity buyer, String payStatus, String fulfillStatus, int minutesAgo) {
        return createOrderSeed(buyer, payStatus, fulfillStatus, minutesAgo, new BigDecimal("980.00"));
    }

    private String createOrderSeed(
            UserAccountEntity buyer,
            String payStatus,
            String fulfillStatus,
            int minutesAgo,
            BigDecimal orderAmount
    ) {
        ProductEntity product = new ProductEntity();
        product.setSnCode("SN-ORDER-LIST-" + UUID.randomUUID().toString().substring(0, 8));
        product.setBrand("OrderListBrand");
        product.setModel("OrderListModel");
        product.setProductionDate(LocalDate.of(2024, 1, 1));
        product.setImageUrl("https://example.com/order-list.jpg");
        product.setWearScore(90);
        product.setRecycleGrade("GOOD");
        product.setEstimatedRecyclePrice(new BigDecimal("800.00"));
        productRepository.save(product);

        RecycleOrderEntity recycleOrder = new RecycleOrderEntity();
        recycleOrder.setOrderNo("RCY-ORDER-LIST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        recycleOrder.setUser(buyer);
        recycleOrder.setProduct(product);
        recycleOrder.setEstimatedPrice(new BigDecimal("800.00"));
        recycleOrder.setGrade("GOOD");
        recycleOrder.setStatus("LISTED");
        recycleOrder.setCreatedAt(LocalDateTime.now().minusDays(1));
        recycleOrderRepository.save(recycleOrder);

        ResaleListingEntity listing = new ResaleListingEntity();
        listing.setRecycleOrder(recycleOrder);
        listing.setProduct(product);
        listing.setSalePrice(new BigDecimal("980.00"));
        listing.setStock(1);
        listing.setStatus("ON_SHELF");
        listing.setCreatedAt(LocalDateTime.now().minusDays(1));
        resaleListingRepository.save(listing);

        ResaleOrderEntity order = new ResaleOrderEntity();
        String orderNo = "B2C-ORDER-LIST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setOrderNo(orderNo);
        order.setBuyerUser(buyer);
        order.setListing(listing);
        order.setAmount(orderAmount);
        order.setPayStatus(payStatus);
        order.setFulfillStatus(fulfillStatus);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(minutesAgo));
        resaleOrderRepository.save(order);
        return orderNo;
    }
}
