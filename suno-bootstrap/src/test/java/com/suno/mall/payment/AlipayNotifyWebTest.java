package com.suno.mall.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.service.RecycleApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAY-003 支付宝异步通知 Web 测试：合法通知落账、重放幂等、篡改/金额不符拒绝、
 * 渠道启用时 /orders/pay 返回收银台跳转参数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlipayNotifyWebTest {

    private static final String PRIVATE_KEY = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDKSuhIrzjs7Mm0bJDva6VC2qr3j02sVK3XhgZWIGA7PC86TJyQUaG48NX/+FIkeX8s3UnT9BLaCgeAyjHc0kTsLZ9/tb5F5kw94nFm/xkZMXM/AmKl2xczJZPxKpWwOQnj5mzj+hZAYOSJNW74uVRzKoHP7N9BoEb60K3NYgoV9nA5bXOkDkBFkF0FsNnF1yo20C4XsP9qARiU3DpEclMf0emKHQYilm0tVxemkH7RC8LAQAqaPO0znsooV/ioMUNaBFE7pPD8CTaYs59Z+ucYuxX49gJw9rTaQlt7pSLQ/9oth//OpZ24SNtLP1QeHyJuBpbMdjf9HDNSGLsGf3JlAgMBAAECggEASC3iI80hqqfycIYsUME//lK3ng1r0YmFXdlceW6j537Z+xtK4dO+uJZ2RRb9eDZBwmhS52W3NEVP2AxNLlV4ciz1NERM1/QMUChz/iQXeH3EeT8Io9VAuec2ml5veqlfdgnJ4K5jMiVgZfTC28yE7XLFTsNeBbSCnwvGd/n7T0o0MkqFS3ELrTNBoqxaY45y6UXFP7SzwnJxCwLSNxDD9ZBK6cm9JlEGeTxovQpICPdvoHy5PcgTRhLAT3i2EpSloo9VcSv/J+fxVi+NCx2Mk4gmA1rRRFSgvXB98I7FCmNfSmwo8iXbEUh5H1FsOd7sHfuQKCehhOLuAIAlyTy4IQKBgQD6DvofYaImaRJgYpBFXuD/BGcVcEUppkMHXio2FYbRlctl7oz4mxGs6owmoD9UXBippxErSGjJB3PZ/JNpC0stKx/8QwDRWOg8oKRAYxUscE052vRI5aHl5C0XQq5Uw19ERXtCu9Q2b1RyBtQHGURiclsVoq+eIKBe6Fg0xaBCjwKBgQDPGWLeT1CcM49AzE/+SfaJLLXVa1Gyp2LE9VFE+QZcP3J/HkVFJJJceynbo5R11Cf9mURPkkYpGOvluXaNj/6bZyb7MedLeQMY/kTCmkutVXAeumryZNGOZCjClzzPAQLVVuPNHpEmOLaj3IUZOO5Nde/Wmg6pfSWH1FvTInQlywKBgQDQQHDmebVFL/Rx+SQV4fKX79656ApxqVnhZPbanArvuUTv2z/kO6D41U1rTcHwS9LuV4jg9yjtYxuEy+kobpNpRqUYveSMdPLUqySfyuTzYYOvTz5rB3NRtjonSQ1O4ncQsJz68trFiupVHIKwdsaRBpP87MQ9eQaWhAIR0RSq2QKBgQCNk2s1Ur9mqRCrWYDt854oFHqR1XXaTfeFcbcHEb45Xd5l7EmqhN/RfRkWX3NkQY69HQPH6dUwzyqktu7Gw8NodVH+sxTNUYK+mURkTJqjeh7VwtbNsgJX9zyuGjmlwakwMuA0H0tW97pTtrL+lR7DsXYuHM8dKsJ3YsMLSShhlQKBgQC6z8AzzPfymkQGVDCNq1i0avNRlMUEdM5mkqRGEkSbo0LzkU1pqNEaeTCU9jXw2LO7B3VTSYxaa8F1XIJB//q+F/DhA+P2xUUkTs915edWVfy5PXrresjfnf0zyoSOwKEaAlUxtDvdtO42uUumdzOn+eSAc3Fzz7oM8nLFVXDIKw";
    private static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAykroSK847OzJtGyQ72ulQtqq949NrFSt14YGViBgOzwvOkyckFGhuPDV//hSJHl/LN1J0/QS2goHgMox3NJE7C2ff7W+ReZMPeJxZv8ZGTFzPwJipdsXMyWT8SqVsDkJ4+Zs4/oWQGDkiTVu+LlUcyqBz+zfQaBG+tCtzWIKFfZwOW1zpA5ARZBdBbDZxdcqNtAuF7D/agEYlNw6RHJTH9Hpih0GIpZtLVcXppB+0QvCwEAKmjztM57KKFf4qDFDWgRRO6Tw/Ak2mLOfWfrnGLsV+PYCcPa02kJbe6Ui0P/aLYf/zqWduEjbSz9UHh8ibgaWzHY3/RwzUhi7Bn9yZQIDAQAB";
    private static final String APP_ID = "2021000000000001";

    @DynamicPropertySource
    static void alipayProperties(DynamicPropertyRegistry registry) {
        registry.add("payment.alipay.enabled", () -> "true");
        registry.add("payment.alipay.app-id", () -> APP_ID);
        registry.add("payment.alipay.private-key", () -> PRIVATE_KEY);
        registry.add("payment.alipay.alipay-public-key", () -> PUBLIC_KEY);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecycleApplicationService recycleApplicationService;

    @Autowired
    private AlipaySignatureService alipaySignatureService;

    private String registerAndLogin(String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.suno.mall.controller.AuthController.RegisterRequest("ali_" + suffix, "Str0ngPass!2026", "web-test"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private String createUnpaidOrder(String token, String snSuffix) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        long userId = objectMapper.readTree(me.getResponse().getContentAsString()).at("/data/userId").asLong();
        Map<String, Object> recycle = recycleApplicationService.createRecycleOrder(
                userId, "SN-ALI-" + snSuffix, "https://cdn.suno.test/ali.jpg", 90, 0);
        String recycleOrderNo = (String) recycle.get("orderNo");
        recycleApplicationService.transitionOrder(recycleOrderNo, "QUALITY_CHECK", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "PRICE_REVIEW", null);
        recycleApplicationService.transitionOrder(recycleOrderNo, "LIST_ON_SHELF", null);
        Long listingId = ((Number) recycleApplicationService
                .publishResaleListing(recycleOrderNo, new BigDecimal("1899.00"), 1).get("listingId")).longValue();
        MvcResult order = mockMvc.perform(post("/api/mall/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingId))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(order.getResponse().getContentAsString()).at("/data/orderNo").asText();
    }

    private MockHttpServletRequestBuilder notifyRequest(Map<String, String> params) {
        MockHttpServletRequestBuilder request = post("/api/payment/alipay/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        params.forEach(request::param);
        return request;
    }

    private Map<String, String> signedNotify(String orderNo, String tradeNo, String amount) {
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", APP_ID);
        params.put("charset", "utf-8");
        params.put("out_trade_no", orderNo);
        params.put("trade_no", tradeNo);
        params.put("total_amount", amount);
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("sign_type", "RSA2");
        params.put("sign", alipaySignatureService.sign(params, PRIVATE_KEY));
        return params;
    }

    @Test
    void validNotifyMarksOrderPaidAndReplaysAreIdempotent() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("ok_" + nonce);
        String orderNo = createUnpaidOrder(token, "N" + nonce);

        Map<String, String> notify = signedNotify(orderNo, "2026102422001400001", "1899.00");
        String body = mockMvc.perform(notifyRequest(notify)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("success", body);
        assertEquals("PAID", recycleApplicationService.getOrderAmountForGateway(orderNo).get("payStatus"));

        String replay = mockMvc.perform(notifyRequest(notify)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("success", replay);
        assertEquals("PAID", recycleApplicationService.getOrderAmountForGateway(orderNo).get("payStatus"));
    }

    @Test
    void rejectsTamperedSignAndAmountMismatch() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("bad_" + nonce);
        String orderNo = createUnpaidOrder(token, "B" + nonce);

        Map<String, String> tampered = signedNotify(orderNo, "2026102422001400002", "1899.00");
        tampered.put("sign", tampered.get("sign").substring(0, tampered.get("sign").length() - 4) + "AAAA");
        assertEquals("failure", mockMvc.perform(notifyRequest(tampered)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("UNPAID", recycleApplicationService.getOrderAmountForGateway(orderNo).get("payStatus"));

        Map<String, String> wrongAmount = signedNotify(orderNo, "2026102422001400003", "0.01");
        assertEquals("failure", mockMvc.perform(notifyRequest(wrongAmount)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("UNPAID", recycleApplicationService.getOrderAmountForGateway(orderNo).get("payStatus"));
    }

    @Test
    void payOrderReturnsAlipayPagePayWhenEnabled() throws Exception {
        long nonce = System.nanoTime();
        String token = registerAndLogin("pay_" + nonce);
        String orderNo = createUnpaidOrder(token, "P" + nonce);
        MvcResult pay = mockMvc.perform(post("/api/mall/orders/pay").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.suno.mall.controller.ResaleMallController.PayResaleOrderRequest(
                                orderNo, "any-key", System.currentTimeMillis() / 1000, "nonce", "sig"))))
                .andExpect(status().isOk()).andReturn();
        String payUrl = objectMapper.readTree(pay.getResponse().getContentAsString()).at("/data/payUrl").asText();
        assertTrue(payUrl.contains("gateway.do"), "应返回支付宝收银台跳转 URL");
        assertTrue(payUrl.contains("sign="));
    }
}
