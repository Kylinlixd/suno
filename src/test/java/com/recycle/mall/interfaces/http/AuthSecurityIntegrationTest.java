package com.recycle.mall.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest extends TestAuthSupport {

    @Test
    void shouldLoginAndAccessMe() throws Exception {
        Tokens tokens = loginAndGetTokens("alice", "user123");
        MvcResult meResult = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(meResult.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
        assertEquals("alice", root.get("data").get("username").asText());
        assertEquals("USER", root.get("data").get("role").asText());
    }

    @Test
    void shouldReturn401WhenNoToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertEquals("请先登录", root.get("message").asText());
    }

    @Test
    void shouldForbidUserAccessAdminApi() throws Exception {
        Tokens userTokens = loginAndGetTokens("alice", "user123");
        MvcResult result = mockMvc.perform(get("/api/admin/payment/replay-tasks/summary")
                        .header("Authorization", "Bearer " + userTokens.accessToken()))
                .andExpect(status().isForbidden())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertEquals("无权限访问该资源", root.get("message").asText());
    }

    @Test
    void shouldAllowAdminAccessAdminApi() throws Exception {
        Tokens adminTokens = loginAndGetTokens("admin", "admin123");
        MvcResult result = mockMvc.perform(get("/api/admin/payment/replay-tasks/summary")
                        .header("Authorization", "Bearer " + adminTokens.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
    }

    @Test
    void shouldRefreshAccessToken() throws Exception {
        Tokens tokens = loginAndGetTokens("alice", "user123", "ios-1");
        String body = "{\"refreshToken\":\"" + tokens.refreshToken() + "\",\"deviceId\":\"ios-1\"}";
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
        assertNotNull(root.get("data").get("accessToken"));
        assertNotNull(root.get("data").get("refreshToken"));
    }

    @Test
    void shouldRejectBlacklistedTokenAfterLogout() throws Exception {
        Tokens tokens = loginAndGetTokens("alice", "user123", "android-1");
        String logoutBody = "{\"refreshToken\":\"" + tokens.refreshToken() + "\"}";
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldBlockReplayRefreshTokenReuse() throws Exception {
        Tokens tokens = loginAndGetTokens("alice", "user123", "web-1");
        String refreshBody = "{\"refreshToken\":\"" + tokens.refreshToken() + "\",\"deviceId\":\"web-1\"}";

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk());

        MvcResult replayResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode replayRoot = objectMapper.readTree(replayResult.getResponse().getContentAsString());
        assertFalse(replayRoot.get("success").asBoolean());
        assertEquals("检测到refresh token重放，已阻断会话", replayRoot.get("message").asText());
    }

    @Test
    void shouldRejectRefreshWhenDeviceMismatch() throws Exception {
        Tokens tokens = loginAndGetTokens("alice", "user123", "ipad-1");
        String refreshBody = "{\"refreshToken\":\"" + tokens.refreshToken() + "\",\"deviceId\":\"ipad-2\"}";
        MvcResult mismatchResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(mismatchResult.getResponse().getContentAsString());
        assertFalse(root.get("success").asBoolean());
        assertEquals("refreshToken与设备不匹配", root.get("message").asText());
    }

    @Test
    void shouldListAndRevokeDeviceSessions() throws Exception {
        Tokens current = loginAndGetTokens("alice", "user123", "mac-1");
        loginAndGetTokens("alice", "user123", "iphone-1");

        MvcResult listResult = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listRoot = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertTrue(listRoot.get("success").asBoolean());
        assertTrue(listRoot.get("data").get("total").asInt() >= 2);

        String revokeBody = "{\"deviceId\":\"iphone-1\"}";
        MvcResult revokeResult = mockMvc.perform(post("/api/auth/sessions/revoke-device")
                        .header("Authorization", "Bearer " + current.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revokeBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode revokeRoot = objectMapper.readTree(revokeResult.getResponse().getContentAsString());
        assertTrue(revokeRoot.get("success").asBoolean());
        assertEquals("iphone-1", revokeRoot.get("data").get("deviceId").asText());
    }

    @Test
    void shouldRevokeAllSessions() throws Exception {
        Tokens current = loginAndGetTokens("alice", "user123", "web-a");
        loginAndGetTokens("alice", "user123", "web-b");

        MvcResult revokeAllResult = mockMvc.perform(post("/api/auth/sessions/revoke-all")
                        .header("Authorization", "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode revokeRoot = objectMapper.readTree(revokeAllResult.getResponse().getContentAsString());
        assertTrue(revokeRoot.get("success").asBoolean());
        assertTrue(revokeRoot.get("data").get("revokedCount").asInt() >= 1);
    }

    @Test
    void shouldAllowAdminManageUserSessions() throws Exception {
        loginAndGetTokens("alice", "user123", "android-z");
        Tokens admin = loginAndGetTokens("admin", "admin123", "admin-web");
        MvcResult list = mockMvc.perform(get("/api/admin/auth/sessions")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("username", "alice"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(list.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
        assertEquals("alice", root.get("data").get("username").asText());
    }

    @Test
    void shouldForbidUserManageOtherSessions() throws Exception {
        Tokens user = loginAndGetTokens("alice", "user123", "user-web");
        mockMvc.perform(get("/api/admin/auth/sessions")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .param("username", "bob"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminViewSecurityEventsSummary() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123", "admin-console");
        MvcResult result = mockMvc.perform(get("/api/admin/auth/security-events/summary")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("lookbackMinutes", "120"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
        assertNotNull(root.get("data").get("counts"));
        assertEquals(120, root.get("data").get("lookbackMinutes").asInt());
    }

    @Test
    void shouldForbidUserViewSecurityEventsSummary() throws Exception {
        Tokens user = loginAndGetTokens("alice", "user123", "user-phone");
        mockMvc.perform(get("/api/admin/auth/security-events/summary")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminViewSecurityEventsTimeline() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123", "admin-pad");
        MvcResult result = mockMvc.perform(get("/api/admin/auth/security-events/timeline")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("lookbackMinutes", "15")
                        .param("actionTypes", "AUTH_REFRESH_REPLAY_BLOCKED")
                        .param("actionTypes", "AUTH_LOGOUT"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
        assertEquals(15, root.get("data").get("lookbackMinutes").asInt());
        assertTrue(root.get("data").get("points").isArray());
        assertTrue(root.get("data").get("actions").isArray());
    }

    @Test
    void shouldAllowAdminViewSecurityRiskUsersTop() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123", "admin-risk");
        MvcResult result = mockMvc.perform(get("/api/admin/auth/security-events/risk-users-top")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("lookbackMinutes", "120")
                        .param("topN", "5")
                        .param("actionTypes", "AUTH_REFRESH_REPLAY_BLOCKED")
                        .param("actionTypes", "AUTH_ADMIN_SESSION_REVOKE_DEVICE"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.get("success").asBoolean());
        assertEquals(5, root.get("data").get("topN").asInt());
        assertTrue(root.get("data").get("users").isArray());
    }

    @Test
    void shouldAllowAdminExportSecurityEventsCsv() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123", "admin-export");
        mockMvc.perform(get("/api/admin/auth/security-events/export")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("type", "summary")
                        .param("format", "csv")
                        .param("lookbackMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"));
    }

    @Test
    void shouldCreateAndDownloadExportTask() throws Exception {
        Tokens admin = loginAndGetTokens("admin", "admin123", "admin-task");
        String body = "{\"type\":\"summary\",\"format\":\"json\",\"lookbackMinutes\":60,\"topN\":10,\"idempotencyKey\":\"exp-key-001\"}";
        MvcResult createResult = mockMvc.perform(post("/api/admin/auth/security-events/export/tasks")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode createRoot = objectMapper.readTree(createResult.getResponse().getContentAsString());
        assertTrue(createRoot.get("success").asBoolean());
        String taskId = createRoot.get("data").get("taskId").asText();
        assertNotNull(taskId);

        mockMvc.perform(get("/api/admin/auth/security-events/export/tasks/" + taskId)
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/auth/security-events/export/tasks/" + taskId + "/download")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk());

        MvcResult createAgainResult = mockMvc.perform(post("/api/admin/auth/security-events/export/tasks")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode createAgainRoot = objectMapper.readTree(createAgainResult.getResponse().getContentAsString());
        assertTrue(createAgainRoot.get("success").asBoolean());
        assertEquals(taskId, createAgainRoot.get("data").get("taskId").asText());
        assertTrue(createAgainRoot.get("data").get("reused").asBoolean());

        mockMvc.perform(get("/api/admin/auth/security-events/export/tasks")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/auth/security-events/export/tasks/cleanup")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retainDays\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/auth/security-events/export/tasks/" + taskId + "/retry")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lookbackMinutes\":60,\"topN\":10}"))
                .andExpect(status().isOk());
    }

}
