package com.suno.mall.service;

import com.recycle.mall.entity.PaymentNonceEntity;
import com.recycle.mall.dao.PaymentNonceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class PaymentSignatureService {

    private final PaymentNonceRepository paymentNonceRepository;

    public PaymentSignatureService(PaymentNonceRepository paymentNonceRepository) {
        this.paymentNonceRepository = paymentNonceRepository;
    }

    @Value("${payment.callback.secret:demo-payment-secret}")
    private String callbackSecret;

    @Value("${payment.callback.max-skew-seconds:300}")
    private long maxSkewSeconds;

    public void verifyOrThrow(String orderNo, String idempotencyKey, long timestamp, String nonce, String signature) {
        cleanupExpiredNonces();
        if (nonce == null || nonce.isBlank()) {
            throw new IllegalArgumentException("支付签名校验失败: nonce 为空");
        }
        if (paymentNonceRepository.findByNonce(nonce).isPresent()) {
            throw new IllegalArgumentException("支付签名校验失败: nonce 重放");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > maxSkewSeconds) {
            throw new IllegalArgumentException("支付签名校验失败: 请求已过期");
        }
        String payload = orderNo + "|" + idempotencyKey + "|" + timestamp + "|" + nonce;
        String expected = hmacSha256Hex(payload, callbackSecret);
        if (!expected.equalsIgnoreCase(signature)) {
            throw new IllegalArgumentException("支付签名校验失败: 签名不匹配");
        }
        PaymentNonceEntity nonceEntity = new PaymentNonceEntity();
        nonceEntity.setNonce(nonce);
        nonceEntity.setCreatedAt(LocalDateTime.now());
        nonceEntity.setExpireAt(LocalDateTime.now().plusSeconds(maxSkewSeconds));
        paymentNonceRepository.save(nonceEntity);
    }

    public void cleanupExpiredNonces() {
        paymentNonceRepository.deleteByExpireAtBefore(LocalDateTime.now());
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                String s = Integer.toHexString(b & 0xff);
                if (s.length() == 1) {
                    hex.append('0');
                }
                hex.append(s);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("签名计算异常", e);
        }
    }
}
