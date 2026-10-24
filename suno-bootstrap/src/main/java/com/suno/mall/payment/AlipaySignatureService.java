package com.suno.mall.payment;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 支付宝 RSA2 签名/验签（纯 JDK，无 SDK）。
 * 规范化：按 key 字典序拼接 k=v&，剔除 sign/sign_type 与空值。
 */
@Service
public class AlipaySignatureService {

    public String canonicalContent(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String key = entry.getKey();
            if ("sign".equals(key) || "sign_type".equals(key)) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(key).append('=').append(value);
        }
        return builder.toString();
    }

    public String sign(Map<String, String> params, String privateKeyBase64) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(privateKeyBase64));
            signature.update(canonicalContent(params).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception failure) {
            throw new IllegalStateException("支付宝签名失败: " + failure.getMessage(), failure);
        }
    }

    public boolean verify(Map<String, String> params, String signBase64, String publicKeyBase64) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(loadPublicKey(publicKeyBase64));
            signature.update(canonicalContent(params).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signBase64));
        } catch (Exception failure) {
            return false;
        }
    }

    /** 支持 PEM 与裸 Base64 两种输入 */
    static String stripPem(String key) {
        return key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    private static PrivateKey loadPrivateKey(String base64) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripPem(base64));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static PublicKey loadPublicKey(String base64) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripPem(base64));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }
}
