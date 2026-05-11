package com.recycle.mall.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminReviewReportModerationIntegrationTest extends TestAuthSupport {

    @Autowired
    private ReviewReportTestFixture reviewReportTestFixture;

    @Test
    void shouldProcessPendingReportToHiddenWhenApproveHide() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long reportId = reviewReportTestFixture.createPendingReport();
        String body = """
                {
                  "reportId": %d,
                  "action":"APPROVE_HIDE",
                  "processNote":"hide-by-admin",
                  "operator":"admin"
                }
                """.formatted(reportId);
        MvcResult process = mockMvc.perform(post("/api/admin/recycle/review-reports/process")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(process.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertEquals("APPROVED", data.get("status").asText());
        assertEquals("HIDDEN", data.get("reviewModerationStatus").asText());
        assertEquals("admin", data.get("processedBy").asText());
    }

    @Test
    void shouldKeepHiddenModerationWhenRejectingHiddenReviewReport() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long reportId = reviewReportTestFixture.createPendingHiddenReviewReport();
        String body = """
                {
                  "reportId": %d,
                  "action":"REJECT",
                  "processNote":"reject-hidden-report",
                  "operator":"admin"
                }
                """.formatted(reportId);
        mockMvc.perform(post("/api/admin/recycle/review-reports/process")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult detail = mockMvc.perform(get("/api/admin/recycle/review-reports/" + reportId)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(detail.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertEquals("REJECTED", data.get("status").asText());
        assertEquals("HIDDEN", data.get("reviewModerationStatus").asText());
    }

    @Test
    void shouldListApprovedReportsAndKeepDeboostModerationInDetail() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long approvedReportId = reviewReportTestFixture.createApprovedDeboostReport();

        MvcResult list = mockMvc.perform(get("/api/admin/recycle/review-reports")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listRoot = objectMapper.readTree(list.getResponse().getContentAsString());
        JsonNode items = listRoot.get("data").get("items");
        boolean found = false;
        for (JsonNode item : items) {
            if (approvedReportId.equals(item.get("reportId").asLong())) {
                found = true;
                assertEquals("APPROVED", item.get("status").asText());
                assertEquals("DEBOOST", item.get("reviewModerationStatus").asText());
                break;
            }
        }
        assertTrue(found);

        MvcResult detail = mockMvc.perform(get("/api/admin/recycle/review-reports/" + approvedReportId)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detailRoot = objectMapper.readTree(detail.getResponse().getContentAsString());
        JsonNode detailData = detailRoot.get("data");
        assertEquals("APPROVED", detailData.get("status").asText());
        assertEquals("DEBOOST", detailData.get("reviewModerationStatus").asText());
        assertEquals("fixture", detailData.get("processedBy").asText());
    }

    @Test
    void shouldFailWhenProcessingNonPendingReport() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long approvedReportId = reviewReportTestFixture.createApprovedDeboostReport();
        String body = """
                {
                  "reportId": %d,
                  "action":"APPROVE_HIDE",
                  "processNote":"reprocess-should-fail",
                  "operator":"admin"
                }
                """.formatted(approvedReportId);
        MvcResult result = mockMvc.perform(post("/api/admin/recycle/review-reports/process")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.get("message").asText().contains("仅 PENDING 工单可处理"));
    }

    @Test
    void shouldWriteRequestIdForProcessReviewReportAuditLog() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long reportId = reviewReportTestFixture.createPendingReport();
        String requestId = "req-review-process-" + System.currentTimeMillis();
        String body = """
                {
                  "reportId": %d,
                  "action":"APPROVE_DEBOOST",
                  "processNote":"audit-request-id",
                  "operator":"admin",
                  "requestId":"%s",
                  "changeSummary":{"changedCount":1,"changedKeys":["status"]}
                }
                """.formatted(reportId, requestId);
        mockMvc.perform(post("/api/admin/recycle/review-reports/process")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_REVIEW_REPORT_PROCESS")
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
    void shouldAutoGenerateRequestIdWhenMissingForProcessReviewReport() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long reportId = reviewReportTestFixture.createPendingReport();
        String body = """
                {
                  "reportId": %d,
                  "action":"APPROVE_DEBOOST",
                  "processNote":"auto-request-id",
                  "operator":"admin",
                  "changeSummary":{"changedCount":1,"changedKeys":["status"]}
                }
                """.formatted(reportId);
        mockMvc.perform(post("/api/admin/recycle/review-reports/process")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_REVIEW_REPORT_PROCESS")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode item : logs) {
            String detail = item.get("detail").asText();
            if (detail.contains("reportId=" + reportId) && hasAutoGeneratedRequestId(detail)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void shouldAutoGenerateRequestIdWhenBlankForProcessReviewReport() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long reportId = reviewReportTestFixture.createPendingReport();
        String body = """
                {
                  "reportId": %d,
                  "action":"APPROVE_DEBOOST",
                  "processNote":"blank-request-id",
                  "operator":"admin",
                  "requestId":"   ",
                  "changeSummary":{"changedCount":1,"changedKeys":["status"]}
                }
                """.formatted(reportId);
        mockMvc.perform(post("/api/admin/recycle/review-reports/process")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_REVIEW_REPORT_PROCESS")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode item : logs) {
            String detail = item.get("detail").asText();
            if (detail.contains("reportId=" + reportId) && hasAutoGeneratedRequestId(detail)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}
