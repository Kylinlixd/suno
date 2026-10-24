package com.suno.mall.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.dao.UserAccountRepository;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.service.MemberLevelService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 批次测试：会员等级（阈值与只升不降）、退款 CSV 导出（MKT-124）、手动对账（PAY-119）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberFinanceWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberLevelService memberLevelService;

    @Autowired
    private RecycleApplicationService recycleApplicationService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private String registerAndLogin(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.suno.mall.controller.AuthController.RegisterRequest("mf_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private String adminToken(String suffix) throws Exception {
        String username = "mfadm_" + suffix;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new com.suno.mall.controller.AuthController.RegisterRequest(username, "Str0ngPass!2026", "web-test"))));
        UserAccountEntity user = userAccountRepository.findByUsername(username).orElseThrow();
        user.setRoleCode("ADMIN");
        userAccountRepository.save(user);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.suno.mall.controller.AuthController.LoginRequest(username, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    @Test
    void memberLevelFollowsThresholdsAndNeverDowngrades() {
        assertEquals(MemberLevelService.LEVEL_NORMAL, MemberLevelService.levelFor(0));
        assertEquals(MemberLevelService.LEVEL_NORMAL, MemberLevelService.levelFor(999));
        assertEquals(MemberLevelService.LEVEL_SILVER, MemberLevelService.levelFor(1000));
        assertEquals(MemberLevelService.LEVEL_GOLD, MemberLevelService.levelFor(5000));

        UserAccountEntity user = new UserAccountEntity();
        user.setId(990001L);
        user.setUsername("mf_level_probe");
        user.setPasswordHash("{noop}x");
        user.setRoleCode("USER");
        user.setAccountStatus("ACTIVE");
        user.setLevel("SILVER");
        user.setPoints(100);
        // 只升不降：SILVER + 100 分不降回 NORMAL
        org.junit.jupiter.api.Assertions.assertFalse(memberLevelService.recalculate(user));
        org.junit.jupiter.api.Assertions.assertEquals("SILVER", user.getLevel());
        user.setPoints(6000);
        org.junit.jupiter.api.Assertions.assertTrue(memberLevelService.recalculate(user));
        org.junit.jupiter.api.Assertions.assertEquals("GOLD", user.getLevel());
    }

    @Test
    void recyclePointsPushMemberLevelAndNotification() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("lvl_" + nonce);
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        long userId = objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();
        UserAccountEntity user = userAccountRepository.findById(userId).orElseThrow();
        int before = user.getPoints() == null ? 0 : user.getPoints();
        // 直接把积分抬到 GOLD 门槛以上后触发一次回收（入账路径会重算等级）
        user.setPoints(4999);
        userAccountRepository.save(user);
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-LEVEL-" + nonce, "https://cdn.suno.test/lvl.jpg", 90, 0);
        assertTrue(((Number) recycle.get("rewardPoints")).intValue() > 0);
        UserAccountEntity after = userAccountRepository.findById(userId).orElseThrow();
        assertEquals("GOLD", after.getLevel(), "跨过 5000 分应升 GOLD");
        // 升级通知应可查询到
        MvcResult notifications = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        String body = notifications.getResponse().getContentAsString();
        assertTrue(body.contains("MEMBER_LEVEL_UP"), "应有升级通知");
    }

    @Test
    void refundsCsvExportAndManualReconciliationRequireAdmin() throws Exception {
        long nonce = System.nanoTime();
        String admin = adminToken("fin_" + nonce);
        String user = registerAndLogin("fin_" + nonce);

        MvcResult csv = mockMvc.perform(get("/api/admin/recycle/refunds/export").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        String content = csv.getResponse().getContentAsString();
        assertTrue(content.startsWith("refund_no,order_no,amount,status,channel"));

        MvcResult reconcile = mockMvc.perform(post("/api/admin/payment/reconcile/run").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(reconcile.getResponse().getContentAsString()).at("/data/checked").asLong() >= 0);

        // 普通用户无权
        mockMvc.perform(get("/api/admin/recycle/refunds/export").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/payment/reconcile/run").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
    }
}
