package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.service.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M2 用户端售后（MKT-060 ~ MKT-062）。
 */
@Validated
@RestController
@RequestMapping("/api/mall")
public class RefundController {

    private final RefundService refundService;
    private final AuthApplicationService authApplicationService;

    public RefundController(RefundService refundService, AuthApplicationService authApplicationService) {
        this.refundService = refundService;
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/orders/{orderNo}/refund-apply")
    public ApiResponse<Map<String, Object>> applyRefund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderNo,
            @RequestBody @Valid RefundApplyRequest request
    ) {
        return ApiResponse.ok(refundService.applyRefund(orderNo, authApplicationService.requireUserId(jwt), request.reason()));
    }

    @GetMapping("/refunds")
    public ApiResponse<Map<String, Object>> listMyRefunds(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(refundService.listMyRefunds(authApplicationService.requireUserId(jwt), page, size));
    }

    @PostMapping("/refunds/{refundId}/cancel")
    public ApiResponse<Map<String, Object>> cancelRefund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long refundId
    ) {
        return ApiResponse.ok(refundService.cancelRefund(refundId, authApplicationService.requireUserId(jwt)));
    }

    public record RefundApplyRequest(@NotBlank String reason) {
    }
}
