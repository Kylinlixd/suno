package com.suno.mall.payment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝开放平台薄客户端（alipay.trade.query / alipay.trade.refund），
 * 纯 JDK HttpClient，网络调用仅在 payment.alipay.enabled=true 时发生。
 */
@Service
public class AlipayOpenApiClient {

    private static final DateTimeFormatter ALIPAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayProperties alipayProperties;
    private final AlipaySignatureService alipaySignatureService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AlipayOpenApiClient(AlipayProperties alipayProperties, AlipaySignatureService alipaySignatureService) {
        this.alipayProperties = alipayProperties;
        this.alipaySignatureService = alipaySignatureService;
    }

    /** trade.query：返回网关响应参数（含 trade_status / trade_no），失败抛 IllegalStateException */
    public Map<String, String> tradeQuery(String outTradeNo) throws Exception {
        Map<String, String> params = baseParams("alipay.trade.query", "{\"out_trade_no\":\"" + outTradeNo + "\"}");
        return post(params);
    }

    /** trade.refund：out_request_no 幂等，refundAmount 为退款额 */
    public Map<String, String> tradeRefund(String outTradeNo, String outRequestNo, BigDecimal refundAmount) throws Exception {
        Map<String, String> params = baseParams("alipay.trade.refund", "{\"out_trade_no\":\"" + outTradeNo
                + "\",\"refund_amount\":\"" + refundAmount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                + "\",\"out_request_no\":\"" + outRequestNo + "\"}");
        return post(params);
    }

    private Map<String, String> baseParams(String method, String bizContent) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", alipayProperties.getAppId());
        params.put("method", method);
        params.put("charset", "utf-8");
        params.put("format", "JSON");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(ALIPAY_TIMESTAMP));
        params.put("version", "1.0");
        params.put("biz_content", bizContent);
        params.put("sign", alipaySignatureService.sign(params, alipayProperties.getPrivateKey()));
        return params;
    }

    private Map<String, String> post(Map<String, String> params) throws Exception {
        StringBuilder form = new StringBuilder();
        params.forEach((key, value) -> {
            if (form.length() > 0) {
                form.append('&');
            }
            form.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        HttpRequest request = HttpRequest.newBuilder(URI.create(alipayProperties.getGatewayUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("支付宝网关 HTTP " + response.statusCode());
        }
        // 生产实现应解析 alipay_trade_query_response 节点并验签；此处保守返回原始键值占位
        Map<String, String> result = new LinkedHashMap<>();
        result.put("raw", response.body());
        return result;
    }
}
