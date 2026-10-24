package com.suno.mall.payment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 构建支付宝电脑网站支付（alipay.trade.page.pay）的已签名请求参数与跳转 URL。
 * 不发起网络调用——由浏览器携带参数跳转网关，符合官方 page.pay 协议。
 */
@Service
public class AlipayPagePayService {

    private static final DateTimeFormatter ALIPAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayProperties alipayProperties;
    private final AlipaySignatureService alipaySignatureService;

    public AlipayPagePayService(AlipayProperties alipayProperties, AlipaySignatureService alipaySignatureService) {
        this.alipayProperties = alipayProperties;
        this.alipaySignatureService = alipaySignatureService;
    }

    /**
     * @param orderNo 商户订单号（out_trade_no）
     * @param amount 应付金额（两位小数字符串）
     * @param subject 订单标题
     * @return payUrl（网关跳转地址）与已签名的参数表
     */
    public Map<String, Object> buildPagePay(String orderNo, BigDecimal amount, String subject) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", alipayProperties.getAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("charset", "utf-8");
        params.put("format", "JSON");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(ALIPAY_TIMESTAMP));
        params.put("version", "1.0");
        params.put("notify_url", alipayProperties.getNotifyUrl());
        params.put("return_url", alipayProperties.getReturnUrl());
        params.put("biz_content", "{\"out_trade_no\":\"" + orderNo
                + "\",\"total_amount\":\"" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                + "\",\"subject\":\"" + escapeJson(subject)
                + "\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");
        String sign = alipaySignatureService.sign(params, alipayProperties.getPrivateKey());
        params.put("sign", sign);

        StringBuilder url = new StringBuilder(alipayProperties.getGatewayUrl()).append("?charset=utf-8");
        params.forEach((key, value) -> url.append('&').append(key).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "alipay");
        result.put("payUrl", url.toString());
        result.put("params", params);
        return result;
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
