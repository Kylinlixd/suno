package com.recycle.mall.controller;

import com.recycle.mall.service.RecycleApplicationService;
import com.recycle.mall.service.PaymentSignatureService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/payment")
public class PaymentCallbackController {

    private final PaymentSignatureService paymentSignatureService;
    private final RecycleApplicationService recycleApplicationService;

    @Value("${payment.callback.response-mode:json}")
    private String responseMode;

    @Value("${payment.callback.response-success-text:success}")
    private String responseSuccessText;

    @Value("${payment.callback.response-ignored-text:ignored}")
    private String responseIgnoredText;

    public PaymentCallbackController(
            PaymentSignatureService paymentSignatureService,
            RecycleApplicationService recycleApplicationService
    ) {
        this.paymentSignatureService = paymentSignatureService;
        this.recycleApplicationService = recycleApplicationService;
    }

    @PostMapping("/callback")
    public ResponseEntity<String> paymentCallback(
            @RequestHeader("X-Timestamp") long timestamp,
            @RequestHeader("X-Signature") String signature,
            @RequestBody PaymentCallbackRequest request
    ) {
        String callbackStatus = "FAILED";
        String errorMessage = null;
        String responseBody = "";
        try {
            paymentSignatureService.verifyOrThrow(
                    request.orderNo(),
                    request.idempotencyKey(),
                    timestamp,
                    request.nonce(),
                    signature
            );
            if (!"SUCCESS".equalsIgnoreCase(request.payStatus())) {
                callbackStatus = "IGNORED";
                responseBody = getResponseBody("IGNORED", "payStatus not success", responseIgnoredText);
                return buildCallbackResponse(responseBody);
            }
            recycleApplicationService.markResaleOrderPaidWithIdempotency(
                    request.orderNo(),
                    request.idempotencyKey()
            );
            callbackStatus = "SUCCESS";
            responseBody = getResponseBody("SUCCESS", "OK", responseSuccessText);
            return buildCallbackResponse(responseBody);
        } catch (RuntimeException ex) {
            errorMessage = ex.getMessage();
            responseBody = getResponseBody("FAIL", ex.getMessage(), "fail");
            return buildCallbackResponse(responseBody);
        } finally {
            recycleApplicationService.logPaymentCallback(
                    request.orderNo(),
                    request.idempotencyKey(),
                    request.payStatus(),
                    request.nonce(),
                    timestamp,
                    signature,
                    callbackStatus,
                    errorMessage,
                    responseBody,
                    "GATEWAY"
            );
        }
    }

    private String getResponseBody(String code, String message, String plainText) {
        if ("plain".equalsIgnoreCase(responseMode)) {
            return plainText;
        }
        return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
    }

    private ResponseEntity<String> buildCallbackResponse(String responseBody) {
        if ("plain".equalsIgnoreCase(responseMode)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(responseBody);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    public record PaymentCallbackRequest(
            @NotBlank String orderNo,
            @NotBlank String idempotencyKey,
            @NotBlank String payStatus,
            @NotBlank String nonce
    ) {
    }
}
