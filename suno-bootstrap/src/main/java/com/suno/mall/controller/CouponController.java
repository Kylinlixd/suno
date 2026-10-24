package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.service.CouponService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * M2 优惠券（MKT-050 ~ MKT-052）：可领列表、领取、我的券。
 * 核销在下单事务内完成（MKT-024 扩展）。
 */
@Validated
@RestController
@RequestMapping("/api/mall/coupons")
public class CouponController {

    private final CouponService couponService;
    private final AuthApplicationService authApplicationService;

    public CouponController(CouponService couponService, AuthApplicationService authApplicationService) {
        this.couponService = couponService;
        this.authApplicationService = authApplicationService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listClaimable(@AuthenticationPrincipal Jwt jwt) {
        authApplicationService.requireUserId(jwt);
        return ApiResponse.ok(couponService.listClaimable());
    }

    @PostMapping("/{couponId}/claim")
    public ApiResponse<Map<String, Object>> claim(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long couponId
    ) {
        return ApiResponse.ok(couponService.claim(authApplicationService.requireUserId(jwt), couponId));
    }

    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> listMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(couponService.listMine(authApplicationService.requireUserId(jwt), status, page, size));
    }
}
