package com.recycle.mall.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRecycleCacheIntegrationTest extends TestAuthSupport {

    @Test
    void shouldReturn304ForReviewStrategyWhenIfNoneMatchMatches() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult first = mockMvc.perform(get("/api/admin/recycle/review-strategy")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(etag);

        mockMvc.perform(get("/api/admin/recycle/review-strategy")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void shouldReturn304ForErrorCodesWhenIfNoneMatchMatches() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult first = mockMvc.perform(get("/api/admin/recycle/error-codes/global")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(etag);

        mockMvc.perform(get("/api/admin/recycle/error-codes/global")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void shouldExposeI18nFieldsInGlobalErrorCodes() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult result = mockMvc.perform(get("/api/admin/recycle/error-codes/global")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode items = root.get("data").get("items");
        assertTrue(items.isArray());
        assertTrue(items.size() > 0);
        JsonNode first = items.get(0);
        assertTrue(first.has("defaultMessageI18n"));
        assertTrue(first.get("defaultMessageI18n").has("zh-CN"));
        assertTrue(first.get("defaultMessageI18n").has("en-US"));
        assertTrue(first.has("recommendedActionI18n"));
        assertTrue(first.get("recommendedActionI18n").has("zh-CN"));
        assertTrue(first.get("recommendedActionI18n").has("en-US"));
    }

    @Test
    void shouldUpdateAlertNoiseRulesAndExposeModule() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        String updateBody = """
                {
                  "updates":{
                    "allowlistKeys":["config.sync.consecutive_failures","config.sync.latency_high"],
                    "quietHoursStart":"01:00",
                    "quietHoursEnd":"06:30",
                    "quietHourPassLevels":["WARN","CRITICAL"]
                  },
                  "operator":"admin"
                }
                """;
        MvcResult updateResult = mockMvc.perform(post("/api/admin/recycle/alert-noise-rules/update")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode updateRoot = objectMapper.readTree(updateResult.getResponse().getContentAsString());
        JsonNode rules = updateRoot.get("data").get("rules");
        assertTrue(rules.get("allowlistKeys").toString().contains("config.sync.latency_high"));
        assertEquals("01:00", rules.get("quietHours").get("start").asText());
        assertEquals("06:30", rules.get("quietHours").get("end").asText());
        JsonNode fieldMeta = updateRoot.get("data").get("fieldMeta");
        assertTrue(fieldMeta.isArray());
        assertTrue(fieldMeta.size() >= 3);
        JsonNode firstField = fieldMeta.get(0);
        assertTrue(firstField.has("groupKey"));
        assertTrue(firstField.has("labelI18n"));
        assertTrue(firstField.get("labelI18n").has("zh-CN"));
        JsonNode groupMeta = updateRoot.get("data").get("groupMeta");
        assertTrue(groupMeta.isArray());
        assertTrue(groupMeta.size() >= 2);

        MvcResult modulesResult = mockMvc.perform(get("/api/admin/recycle/config-center/modules")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(modulesResult.getResponse().getContentAsString())
                .get("data").get("items");
        assertTrue(items.toString().contains("alertNoiseRules"));
    }

    @Test
    void shouldChangeReviewStrategyEtagAfterUpdate() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult before = mockMvc.perform(get("/api/admin/recycle/review-strategy")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String beforeEtag = before.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(beforeEtag);

        String updateBody = """
                {
                  "updates": {
                    "deboostPenalty": 31
                  },
                  "operator": "admin"
                }
                """;
        mockMvc.perform(post("/api/admin/recycle/review-strategy/update")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        MvcResult after = mockMvc.perform(get("/api/admin/recycle/review-strategy")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        String afterEtag = after.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(afterEtag);
        assertTrue(!beforeEtag.equals(afterEtag));
    }

    @Test
    void shouldUpdateAppendWindowDaysViaReviewStrategy() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        String updateBody = """
                {
                  "updates": {
                    "appendWindowDays": 45
                  },
                  "operator": "admin"
                }
                """;
        MvcResult updateResult = mockMvc.perform(post("/api/admin/recycle/review-strategy/update")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode updateRoot = objectMapper.readTree(updateResult.getResponse().getContentAsString());
        assertEquals(45, updateRoot.get("data").get("appendWindowDays").asInt());

        MvcResult queryResult = mockMvc.perform(get("/api/admin/recycle/review-strategy")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode queryRoot = objectMapper.readTree(queryResult.getResponse().getContentAsString());
        assertEquals(45, queryRoot.get("data").get("appendWindowDays").asInt());
    }

    @Test
    void shouldWriteRequestIdAndChangeSummaryToReviewStrategyAuditLog() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        String requestId = "req-review-strategy-" + System.currentTimeMillis();
        String updateBody = """
                {
                  "updates": {
                    "appendWindowDays": 47
                  },
                  "operator": "admin",
                  "requestId": "%s",
                  "changeSummary": {
                    "changedCount": 1,
                    "changedKeys": ["appendWindowDays"]
                  }
                }
                """.formatted(requestId);
        mockMvc.perform(post("/api/admin/recycle/review-strategy/update")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "REVIEW_STRATEGY_CONFIG_UPDATE")
                        .param("targetId", "ACTIVE")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logsRoot = objectMapper.readTree(logsResult.getResponse().getContentAsString());
        JsonNode items = logsRoot.get("data");
        assertTrue(items.isArray());
        boolean found = false;
        for (JsonNode item : items) {
            if (item.get("detail").asText().contains("requestId=" + requestId)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void shouldExposeReviewStrategyFieldMetaI18n() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult result = mockMvc.perform(get("/api/admin/recycle/review-strategy")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode fieldMeta = root.get("data").get("fieldMeta");
        assertTrue(fieldMeta.isArray());
        assertTrue(fieldMeta.size() >= 5);
        JsonNode first = fieldMeta.get(0);
        assertTrue(first.has("displayOrder"));
        assertTrue(first.has("groupKey"));
        assertTrue(first.has("editable"));
        assertTrue(first.has("key"));
        assertTrue(first.has("uiComponentHint"));
        assertTrue(first.has("unit"));
        assertTrue(first.has("step"));
        assertTrue(first.has("labelI18n"));
        assertTrue(first.get("labelI18n").has("zh-CN"));
        assertTrue(first.get("labelI18n").has("en-US"));
        assertTrue(first.has("descriptionI18n"));
        assertTrue(first.has("validationMessageI18n"));
        assertTrue(first.get("validationMessageI18n").has("zh-CN"));
        assertTrue(first.get("validationMessageI18n").has("en-US"));

        JsonNode groupMeta = root.get("data").get("groupMeta");
        assertTrue(groupMeta.isArray());
        assertTrue(groupMeta.size() >= 2);
        JsonNode firstGroup = groupMeta.get(0);
        assertTrue(firstGroup.has("groupKey"));
        assertTrue(firstGroup.has("displayOrder"));
        assertTrue(firstGroup.has("titleI18n"));
        assertTrue(firstGroup.get("titleI18n").has("zh-CN"));
        assertTrue(firstGroup.get("titleI18n").has("en-US"));
    }

    @Test
    void shouldMarkReviewStrategyChangedInModuleDiffAfterAppendWindowUpdate() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult modulesBefore = mockMvc.perform(get("/api/admin/recycle/config-center/modules")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode beforeItems = objectMapper.readTree(modulesBefore.getResponse().getContentAsString())
                .get("data").get("items");
        String beforeDigest = "";
        for (JsonNode item : beforeItems) {
            if ("reviewStrategy".equals(item.get("name").asText())) {
                beforeDigest = item.get("digest").asText();
                break;
            }
        }
        assertTrue(!beforeDigest.isBlank());

        String updateBody = """
                {
                  "updates": {
                    "appendWindowDays": 46
                  },
                  "operator": "admin"
                }
                """;
        mockMvc.perform(post("/api/admin/recycle/review-strategy/update")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        String diffBody = """
                {
                  "clientVersion":"1.0.0",
                  "localDigests":{
                    "reviewStrategy":"%s"
                  }
                }
                """.formatted(beforeDigest);
        MvcResult diffResult = mockMvc.perform(post("/api/admin/recycle/config-center/module-diff")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diffBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode diffRoot = objectMapper.readTree(diffResult.getResponse().getContentAsString());
        JsonNode changed = diffRoot.get("data").get("changed");
        assertTrue(changed.isArray());
        boolean hasReviewStrategy = false;
        for (JsonNode item : changed) {
            if ("reviewStrategy".equals(item.get("name").asText())) {
                hasReviewStrategy = true;
                break;
            }
        }
        assertTrue(hasReviewStrategy);
    }

    @Test
    void shouldReturn304ForConfigCenterBundleWhenIfNoneMatchMatches() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult first = mockMvc.perform(get("/api/admin/recycle/config-center/bundle")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(etag);

        mockMvc.perform(get("/api/admin/recycle/config-center/bundle")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void shouldMarkStepsIncompatibleForPreReleaseClientVersion() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult result = mockMvc.perform(get("/api/admin/recycle/config-center/bundle")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("clientVersion", "1.0.0-rc.1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode compatibility = root.get("data").get("compatibility");
        assertFalse(compatibility.get("allStepsCompatible").asBoolean());
        assertTrue("CLIENT_VERSION_UNSUPPORTED".equals(compatibility.get("errorCode").asText()));
        assertTrue("UNSUPPORTED".equals(compatibility.get("status").asText()));
    }

    @Test
    void shouldReturnWarnWhenOnlyOptionalStepIncompatible() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult result = mockMvc.perform(get("/api/admin/recycle/config-center/bundle")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("clientVersion", "1.0.0"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode compatibility = root.get("data").get("compatibility");
        JsonNode steps = root.get("data").get("bootstrapPlan").get("steps");
        assertTrue("WARN".equals(compatibility.get("status").asText()));
        assertTrue("NONE".equals(compatibility.get("errorCode").asText()));
        assertTrue(steps.isArray());
        assertTrue(steps.size() >= 4);
        assertTrue(steps.get(3).has("degradeAction"));
        assertTrue(steps.get(3).get("degradeAction").has("type"));
        assertTrue(steps.get(3).get("degradeAction").has("params"));
    }

    @Test
    void shouldReturn400WhenModuleNameUnsupported() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult result = mockMvc.perform(get("/api/admin/recycle/config-center/module/not-exists")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(!root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains("不支持的模块名"));
    }

    @Test
    void shouldListSupportedConfigCenterModules() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult result = mockMvc.perform(get("/api/admin/recycle/config-center/modules")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode items = root.get("data").get("items");
        assertTrue(items.isArray());
        assertTrue(items.toString().contains("reviewStrategy"));
        assertTrue(items.toString().contains("bootstrapPlan"));
        assertTrue(items.get(0).has("digest"));
    }

    @Test
    void shouldReturnModuleDiffResult() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        MvcResult modulesResult = mockMvc.perform(get("/api/admin/recycle/config-center/modules")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode modulesRoot = objectMapper.readTree(modulesResult.getResponse().getContentAsString());
        JsonNode items = modulesRoot.get("data").get("items");
        String reviewDigest = "";
        for (JsonNode item : items) {
            if ("reviewStrategy".equals(item.get("name").asText())) {
                reviewDigest = item.get("digest").asText();
                break;
            }
        }
        String body = """
                {
                  "clientVersion":"1.0.0",
                  "localDigests":{
                    "reviewStrategy":"%s",
                    "globalErrorCodes":"stale-digest"
                  }
                }
                """.formatted(reviewDigest);
        MvcResult diffResult = mockMvc.perform(post("/api/admin/recycle/config-center/module-diff")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(diffResult.getResponse().getContentAsString());
        assertEquals("1.0.0", root.get("data").get("clientVersion").asText());
        assertTrue(root.get("data").get("changedCount").asInt() >= 1);
        assertTrue(root.get("data").has("requestHash"));
        assertFalse(root.get("data").get("cacheHit").asBoolean());

        MvcResult secondDiffResult = mockMvc.perform(post("/api/admin/recycle/config-center/module-diff")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondRoot = objectMapper.readTree(secondDiffResult.getResponse().getContentAsString());
        assertTrue(secondRoot.get("data").get("cacheHit").asBoolean());
    }

}
