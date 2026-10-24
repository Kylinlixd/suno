package com.suno.mall.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.dao.EventOutboxRepository;
import com.suno.mall.dao.UserAccountRepository;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.controller.EventOutboxDispatcher;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 最终批次测试：outbox 事件（MKT-E002/PAY-E002 + OPS-S002 派发）、客服工单（OPS-070~073）、
 * 角色细分（OPERATOR/FINANCE 边界）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformBatchWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecycleApplicationService recycleApplicationService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private EventOutboxRepository eventOutboxRepository;

    @Autowired
    private EventOutboxDispatcher eventOutboxDispatcher;

    private String registerAndLogin(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.suno.mall.controller.AuthController.RegisterRequest("plat_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private String loginAsRole(String suffix, String role) throws Exception {
        String username = "plat_" + role.toLowerCase() + "_" + suffix;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new com.suno.mall.controller.AuthController.RegisterRequest(username, "Str0ngPass!2026", "web-test"))));
        UserAccountEntity user = userAccountRepository.findByUsername(username).orElseThrow();
        user.setRoleCode(role);
        userAccountRepository.save(user);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.suno.mall.controller.AuthController.LoginRequest(username, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private String createPaidOrder(String token, String snSuffix) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        long userId = objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-PLAT-" + snSuffix, "https://cdn.suno.test/plat.jpg", 90, 0);
        String recycleOrderNo = (String) recycle.get("orderNo");
        recycleApplicationService.transitionOrder(recycleOrderNo, "QUALITY_CHECK", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "PRICE_REVIEW", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "LIST_ON_SHELF", null);
        Long listingId = ((Number) recycleApplicationService
                .publishResaleListing(recycleOrderNo, new BigDecimal("1299.00"), 1).get("listingId")).longValue();
        MvcResult order = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingId))))
                .andExpect(status().isOk()).andReturn();
        String orderNo = objectMapper.readTree(order.getResponse().getContentAsString()).at("/data/orderNo").asText();
        recycleApplicationService.markResaleOrderPaidWithIdempotency(orderNo, "pay-plat-" + snSuffix);
        return orderNo;
    }

    @Test
    void orderAndPaymentPublishOutboxEventsAndDispatcherMarksPublished() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("obx_" + nonce);
        createPaidOrder(token, "O" + nonce);

        boolean orderCreated = eventOutboxRepository.findAll().stream()
                .anyMatch(entry -> "MKT-E002".equals(entry.getUseCaseId()) && "MarketplaceOrderCreated".equals(entry.getEventType()));
        boolean paymentApplied = eventOutboxRepository.findAll().stream()
                .anyMatch(entry -> "PAY-E002".equals(entry.getUseCaseId()) && "PaymentApplied".equals(entry.getEventType()));
        assertTrue(orderCreated, "下单应发布 MKT-E002 到 outbox");
        assertTrue(paymentApplied, "支付落账应发布 PAY-E002 到 outbox");

        int dispatched = eventOutboxDispatcher.dispatchPending();
        assertTrue(dispatched >= 2, "派发器应消费 PENDING 事件");
        boolean allPublished = eventOutboxRepository.findAll().stream()
                .noneMatch(entry -> "PENDING".equals(entry.getStatus()));
        assertTrue(allPublished, "派发后无 PENDING 残留");
    }

    @Test
    void supportTicketRoundTripNotifiesBuyer() throws Exception {
        long nonce = System.nanoTime();
        String buyer = registerAndLogin("sup_" + nonce);
        String operator = loginAsRole("sup_" + nonce, "OPERATOR");

        MvcResult created = mockMvc.perform(post("/api/support/tickets").header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.suno.mall.controller.SupportController.CreateTicketRequest(
                                "QUESTION", "收货时间可以指定时间段吗？", null))))
                .andExpect(status().isOk())
                .andReturn();
        String ticketNo = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/ticketNo").asText();

        MvcResult mine = mockMvc.perform(get("/api/support/tickets/my").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(mine.getResponse().getContentAsString().contains(ticketNo));

        MvcResult adminList = mockMvc.perform(get("/api/admin/support/tickets?status=OPEN").header("Authorization", "Bearer " + operator))
                .andExpect(status().isOk())
                .andReturn();
        String listBody = adminList.getResponse().getContentAsString();
        assertTrue(listBody.contains(ticketNo));
        long ticketId = extractTicketId(listBody, ticketNo);

        mockMvc.perform(post("/api/admin/support/tickets/" + ticketId + "/reply").header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.suno.mall.controller.SupportController.ReplyTicketRequest(
                                "可以备注期望时段，发货时优先安排。", false))))
                .andExpect(status().isOk());

        MvcResult notifications = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(notifications.getResponse().getContentAsString().contains("TICKET_REPLIED"));

        // 买家无权访问管理端工单
        mockMvc.perform(get("/api/admin/support/tickets").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleSplitEnforcesFinanceAndOperatorBoundaries() throws Exception {
        long nonce = System.nanoTime();
        String operator = loginAsRole("role_o_" + nonce, "OPERATOR");
        String finance = loginAsRole("role_f_" + nonce, "FINANCE");

        // 运营可审核商品，不可访问用户管理/支付
        mockMvc.perform(get("/api/admin/recycle/listings").header("Authorization", "Bearer " + operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + operator))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/payment/reconcile/run").header("Authorization", "Bearer " + operator))
                .andExpect(status().isForbidden());

        // 财务可对账/看售后，不可审核商品/用户管理
        mockMvc.perform(post("/api/admin/payment/reconcile/run").header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/recycle/refunds").header("Authorization", "Bearer " + finance))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/recycle/listings").header("Authorization", "Bearer " + finance))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + finance))
                .andExpect(status().isForbidden());

        assertFalse(operator.equals(finance));
    }

    private long extractTicketId(String listBody, String ticketNo) throws Exception {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(listBody);
        for (com.fasterxml.jackson.databind.JsonNode row : root.at("/data/content")) {
            if (ticketNo.equals(row.get("ticketNo").asText())) {
                return row.get("ticketId").asLong();
            }
        }
        throw new IllegalStateException("ticket not found: " + ticketNo);
    }
}
