package com.recycle.mall.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminReviewReportBatchIntegrationTest extends TestAuthSupport {

    @Autowired
    private ReviewReportTestFixture reviewReportTestFixture;

    @Test
    void shouldReturnBatchProcessFailuresForInvalidReportIds() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        String body = """
                {
                  "reportIds":[null,99999999],
                  "action":"APPROVE_DEBOOST",
                  "processNote":"batch-test",
                  "operator":"admin"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/admin/recycle/review-reports/process-batch")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertEquals(2, data.get("total").asInt());
        assertEquals(0, data.get("successCount").asInt());
        assertEquals(2, data.get("failedCount").asInt());
        assertTrue(data.get("failedItems").isArray());
    }

    @Test
    void shouldReturnBatchProcessPartialSuccess() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long pendingReportId = reviewReportTestFixture.createPendingReport();
        String body = """
                {
                  "reportIds":[%d,99999999],
                  "action":"APPROVE_DEBOOST",
                  "processNote":"batch-partial-success",
                  "operator":"admin"
                }
                """.formatted(pendingReportId);
        MvcResult result = mockMvc.perform(post("/api/admin/recycle/review-reports/process-batch")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertEquals(2, data.get("total").asInt());
        assertEquals(1, data.get("successCount").asInt());
        assertEquals(1, data.get("failedCount").asInt());
    }

    @Test
    void shouldPartiallyFailWhenBatchContainsNonPendingReport() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long pendingReportId = reviewReportTestFixture.createPendingReport();
        Long approvedReportId = reviewReportTestFixture.createApprovedDeboostReport();
        String body = """
                {
                  "reportIds":[%d,%d],
                  "action":"APPROVE_DEBOOST",
                  "processNote":"batch-mixed-status",
                  "operator":"admin"
                }
                """.formatted(pendingReportId, approvedReportId);
        MvcResult result = mockMvc.perform(post("/api/admin/recycle/review-reports/process-batch")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertEquals(2, data.get("total").asInt());
        assertEquals(1, data.get("successCount").asInt());
        assertEquals(1, data.get("failedCount").asInt());
        JsonNode failedItems = data.get("failedItems");
        assertTrue(failedItems.isArray());
        assertEquals(1, failedItems.size());
        assertEquals(approvedReportId.longValue(), failedItems.get(0).get("reportId").asLong());
        assertTrue(failedItems.get(0).get("message").asText().contains("仅 PENDING 工单可处理"));
    }

    @Test
    void shouldFailBatchItemsWhenActionUnsupported() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long pendingReportId = reviewReportTestFixture.createPendingReport();
        String body = """
                {
                  "reportIds":[%d],
                  "action":"INVALID_ACTION",
                  "processNote":"batch-invalid-action",
                  "operator":"admin"
                }
                """.formatted(pendingReportId);
        MvcResult result = mockMvc.perform(post("/api/admin/recycle/review-reports/process-batch")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertEquals(1, data.get("total").asInt());
        assertEquals(0, data.get("successCount").asInt());
        assertEquals(1, data.get("failedCount").asInt());
        JsonNode failedItems = data.get("failedItems");
        assertTrue(failedItems.isArray());
        assertEquals(1, failedItems.size());
        assertEquals(pendingReportId.longValue(), failedItems.get(0).get("reportId").asLong());
        assertTrue(failedItems.get(0).get("message").asText().contains("处理动作仅支持"));
    }

    @Test
    void shouldWriteRequestIdForBatchProcessAuditLog() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long pendingReportId = reviewReportTestFixture.createPendingReport();
        String requestId = "req-review-batch-" + System.currentTimeMillis();
        String body = """
                {
                  "reportIds":[%d],
                  "action":"APPROVE_DEBOOST",
                  "processNote":"batch-audit",
                  "operator":"admin",
                  "requestId":"%s",
                  "changeSummary":{"changedCount":1,"changedKeys":["status"]}
                }
                """.formatted(pendingReportId, requestId);
        mockMvc.perform(post("/api/admin/recycle/review-reports/process-batch")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_REVIEW_REPORT_PROCESS_BATCH")
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
    void shouldAutoGenerateRequestIdWhenMissingForBatchProcess() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long pendingReportId = reviewReportTestFixture.createPendingReport();
        String body = """
                {
                  "reportIds":[%d],
                  "action":"APPROVE_DEBOOST",
                  "processNote":"batch-auto-request-id",
                  "operator":"admin",
                  "changeSummary":{"changedCount":1,"changedKeys":["status"]}
                }
                """.formatted(pendingReportId);
        mockMvc.perform(post("/api/admin/recycle/review-reports/process-batch")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_REVIEW_REPORT_PROCESS_BATCH")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode item : logs) {
            String detail = item.get("detail").asText();
            if (detail.contains("batch-auto-request-id") && hasAutoGeneratedRequestId(detail)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void shouldAutoGenerateRequestIdWhenBlankForBatchProcess() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123");
        Long pendingReportId = reviewReportTestFixture.createPendingReport();
        String body = """
                {
                  "reportIds":[%d],
                  "action":"APPROVE_DEBOOST",
                  "processNote":"batch-blank-request-id",
                  "operator":"admin",
                  "requestId":"   ",
                  "changeSummary":{"changedCount":1,"changedKeys":["status"]}
                }
                """.formatted(pendingReportId);
        mockMvc.perform(post("/api/admin/recycle/review-reports/process-batch")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        MvcResult logsResult = mockMvc.perform(get("/api/admin/recycle/audit-logs")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("actionType", "RESALE_REVIEW_REPORT_PROCESS_BATCH")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode item : logs) {
            String detail = item.get("detail").asText();
            if (detail.contains("batch-blank-request-id") && hasAutoGeneratedRequestId(detail)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}
