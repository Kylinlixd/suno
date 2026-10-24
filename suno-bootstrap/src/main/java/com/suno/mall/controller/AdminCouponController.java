package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AdminReportService;
import com.suno.mall.service.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * M3 运营后台：优惠券管理（MKT-130~132）与经营报表（OPS-060）。
 */
@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminCouponController {

    private final CouponService couponService;
    private final AdminReportService adminReportService;

    public AdminCouponController(CouponService couponService, AdminReportService adminReportService) {
        this.couponService = couponService;
        this.adminReportService = adminReportService;
    }

    @GetMapping("/coupons")
    public ApiResponse<List<Map<String, Object>>> listCoupons(@RequestParam(required = false) String status) {
        return ApiResponse.ok(couponService.adminListCoupons(status));
    }

    @PostMapping("/coupons")
    public ApiResponse<Map<String, Object>> createCoupon(@RequestBody @Valid CreateCouponRequest request) {
        return ApiResponse.ok(couponService.adminCreateCoupon(
                request.name(), request.couponType() == null ? "FIXED" : request.couponType(),
                request.minOrderAmount(), request.discountValue(),
                request.totalCount(), request.perUserLimit(), request.effectiveDays()));
    }

    @PostMapping("/coupons/{couponId}/disable")
    public ApiResponse<Map<String, Object>> disableCoupon(@PathVariable Long couponId) {
        return ApiResponse.ok(couponService.adminDisableCoupon(couponId));
    }

    @GetMapping("/reports/overview")
    public ApiResponse<Map<String, Object>> reportOverview(@RequestParam(required = false) Integer trendDays) {
        return ApiResponse.ok(adminReportService.overview(trendDays));
    }

    public record CreateCouponRequest(
            @NotBlank String name,
            String couponType,
            @NotNull @DecimalMin("0") BigDecimal minOrderAmount,
            @NotNull @DecimalMin("0.01") BigDecimal discountValue,
            @NotNull Integer totalCount,
            @NotNull Integer perUserLimit,
            @NotNull Integer effectiveDays
    ) {
    }
}
