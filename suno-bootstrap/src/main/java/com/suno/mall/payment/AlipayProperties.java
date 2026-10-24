package com.suno.mall.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝渠道配置。enabled=false 时全链路走既有 HMAC Mock 网关；
 * enabled=true 要求 appId/privateKey/alipayPublicKey 齐全（启动期校验）。
 */
@ConfigurationProperties(prefix = "payment.alipay")
public class AlipayProperties {

    private boolean enabled = false;
    private String appId = "";
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    /** 应用私钥（PKCS#8，Base64 或 PEM） */
    private String privateKey = "";
    /** 支付宝公钥（Base64 或 PEM），用于异步通知验签 */
    private String alipayPublicKey = "";
    private String notifyUrl = "";
    private String returnUrl = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getAlipayPublicKey() {
        return alipayPublicKey;
    }

    public void setAlipayPublicKey(String alipayPublicKey) {
        this.alipayPublicKey = alipayPublicKey;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    /** 启动期完整性校验：缺失字段名拼接返回，全部齐备返回 null */
    public String missingField() {
        if (appId == null || appId.isBlank()) {
            return "payment.alipay.app-id";
        }
        if (privateKey == null || privateKey.isBlank()) {
            return "payment.alipay.private-key";
        }
        if (alipayPublicKey == null || alipayPublicKey.isBlank()) {
            return "payment.alipay.alipay-public-key";
        }
        return null;
    }
}
