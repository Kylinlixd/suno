package com.suno.mall.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IDN-008 注册接口 Web 测试：注册成功即可登录，用户名冲突与弱密码被拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRegisterWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registersUserThenLoginWorksAndRejectsDuplicates() throws Exception {
        String username = "reg_" + System.nanoTime();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest(username, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode registerBody = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        assertTrue(registerBody.get("success").asBoolean());
        assertEquals("USER", registerBody.at("/data/role").asText());
        assertTrue(registerBody.at("/data/accessToken").asText().length() > 20, "注册即应签发 access token");
        String accessToken = registerBody.at("/data/accessToken").asText();

        // 注册产生的 BCrypt 哈希必须能通过真实登录链路
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.LoginRequest(username, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk());

        MvcResult duplicate = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest(username, "AnotherPass!2026", "web-test"))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("AUTH_USERNAME_EXISTS", objectMapper.readTree(duplicate.getResponse().getContentAsString()).get("errorCode").asText());
    }

    @Test
    void rejectsWeakPasswordAndBlankUsername() throws Exception {
        MvcResult weakPassword = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest("weakpwd_user", "short", "web-test"))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("AUTH_PASSWORD_INVALID", objectMapper.readTree(weakPassword.getResponse().getContentAsString()).get("errorCode").asText());

        MvcResult badUsername = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest("ab", "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("AUTH_USERNAME_INVALID", objectMapper.readTree(badUsername.getResponse().getContentAsString()).get("errorCode").asText());
    }
}
