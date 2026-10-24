package com.suno.mall.controller;

import com.suno.mall.payment.AlipayProperties;
import com.suno.mall.payment.AlipaySignatureService;
import com.suno.mall.service.RecycleApplicationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * M3 支付宝异步通知（PAY-003）。form-encoded 参数按官方协议 RSA2 验签，
 * 校验 app_id/out_trade_no/total_amount 后驱动幂等落账，成功必须回纯文本 success。
 */
@RestController
@RequestMapping("/api/payment/alipay")
public class AlipayNotifyController {

    private final AlipayProperties alipayProperties;
    private final AlipaySignatureService alipaySignatureService;
    private final RecycleApplicationService recycleApplicationService;

    public AlipayNotifyController(AlipayProperties alipayProperties,
                                  AlipaySignatureService alipaySignatureService,
                                  RecycleApplicationService recycleApplicationService) {
        this.alipayProperties = alipayProperties;
        this.alipaySignatureService = alipaySignatureService;
        this.recycleApplicationService = recycleApplicationService;
    }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> notify(@RequestParam Map<String, String> params) {
        if (!alipayProperties.isEnabled()) {
            return ResponseEntity.status(404).body("failure");
        }
        String sign = params.get("sign");
        if (sign == null || sign.isBlank()
                || !alipaySignatureService.verify(params, sign, alipayProperties.getAlipayPublicKey())) {
            return ResponseEntity.ok().body("failure");
        }
        if (!alipayProperties.getAppId().equals(params.get("app_id"))) {
            return ResponseEntity.ok().body("failure");
        }
        String tradeStatus = params.getOrDefault("trade_status", "");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            // 非成功态通知确认收到但不落账
            return ResponseEntity.ok().body("success");
        }
        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        if (orderNo == null || tradeNo == null) {
            return ResponseEntity.ok().body("failure");
        }
        try {
            BigDecimal notifyAmount = new BigDecimal(params.getOrDefault("total_amount", "-1"));
            var paid = recycleApplicationService.getOrderAmountForGateway(orderNo);
            BigDecimal payable = (BigDecimal) paid.getOrDefault("payableAmount", paid.get("amount"));
            if (payable == null || notifyAmount.compareTo(payable.setScale(2, java.math.RoundingMode.HALF_UP)) != 0) {
                return ResponseEntity.ok().body("failure");
            }
            recycleApplicationService.markResaleOrderPaidWithIdempotency(orderNo, "alipay:" + tradeNo);
            recycleApplicationService.attachResaleOrderPaymentChannel(orderNo, "ALIPAY", tradeNo);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("success");
        } catch (RuntimeException failure) {
            // 状态冲突（如已关闭/已支付回放）按官方语义返回 success 阻断重试；真实异常留待对账
            return ResponseEntity.ok().body("failure");
        }
    }
}
