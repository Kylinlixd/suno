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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IDN-020 ~ IDN-024 收货地址 Web 测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddressWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthController.RegisterRequest("addr_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    @Test
    void createsListsAndSetsSingleDefaultAddress() throws Exception {
        String token = registerAndLogin("def_" + System.nanoTime());
        long first = createAddress(token, "张三", "13800138000", true);
        long second = createAddress(token, "李四", "13900139000", false);

        MvcResult listResult = mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode addresses = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data");
        assertEquals(2, addresses.size());

        // 第二个设为默认后，默认地址应唯一
        mockMvc.perform(post("/api/addresses/" + second + "/default").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        MvcResult afterDefault = mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(afterDefault.getResponse().getContentAsString()).get("data");
        long defaults = 0;
        for (JsonNode row : rows) {
            if (row.get("isDefault").asBoolean()) {
                defaults++;
                assertEquals(second, row.get("addressId").asLong());
            }
        }
        assertEquals(1, defaults);

        // 更新与删除
        mockMvc.perform(put("/api/addresses/" + first).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressController.AddressRequest(
                                "王五", "13800138001", "上海市", "上海市", "浦东新区", "世纪大道 100 号", null))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/addresses/" + first).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        MvcResult afterDelete = mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, objectMapper.readTree(afterDelete.getResponse().getContentAsString()).get("data").size());
    }

    @Test
    void isolatesAddressesBetweenUsers() throws Exception {
        long nonce = System.nanoTime();
        String tokenA = registerAndLogin("iso_a_" + nonce);
        String tokenB = registerAndLogin("iso_b_" + nonce);
        long addressA = createAddress(tokenA, "张三", "13800138000", true);

        MvcResult forbidden = mockMvc.perform(put("/api/addresses/" + addressA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressController.AddressRequest(
                                "黑客", "13000000000", "北京市", "北京市", "朝阳区", "某街道 1 号", null))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertEquals("ADDRESS_NOT_FOUND",
                objectMapper.readTree(forbidden.getResponse().getContentAsString()).get("errorCode").asText());

        MvcResult listB = mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rowsB = objectMapper.readTree(listB.getResponse().getContentAsString()).get("data");
        assertEquals(0, rowsB.size());
        assertFalse(rowsB.size() > 0);
    }

    @Test
    void rejectsBlankReceiverFields() throws Exception {
        String token = registerAndLogin("blank_" + System.nanoTime());
        MvcResult result = mockMvc.perform(post("/api/addresses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressController.AddressRequest(
                                "", "13800138000", "上海市", "上海市", "浦东新区", "世纪大道 100 号", null))))
                .andExpect(status().isBadRequest())
                .andReturn();
        String errorCode = objectMapper.readTree(result.getResponse().getContentAsString()).get("errorCode").asText();
        assertTrue("PARAM_INVALID".equals(errorCode) || "PARAM_BODY_MALFORMED".equals(errorCode));
    }

    private long createAddress(String token, String receiverName, String phone, boolean isDefault) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/addresses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddressController.AddressRequest(
                                receiverName, phone, "上海市", "上海市", "浦东新区", "世纪大道 100 号", isDefault))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/addressId").asLong();
    }
}
