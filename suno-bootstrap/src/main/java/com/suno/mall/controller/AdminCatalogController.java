package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AdminCatalogService;
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
import java.util.Map;

/**
 * M3 运营后台：商品管理（MKT-104~107）与订单管理（MKT-120~121）。
 */
@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;

    public AdminCatalogController(AdminCatalogService adminCatalogService) {
        this.adminCatalogService = adminCatalogService;
    }

    @GetMapping("/recycle/listings")
    public ApiResponse<Map<String, Object>> pageListings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(adminCatalogService.pageListings(status, page, size));
    }

    @PostMapping("/recycle/listings/{listingId}/price")
    public ApiResponse<Map<String, Object>> adjustPrice(
            @PathVariable Long listingId,
            @RequestBody @Valid AdjustPriceRequest request
    ) {
        return ApiResponse.ok(adminCatalogService.adjustPrice(listingId, request.salePrice()));
    }

    @PostMapping("/recycle/listings/{listingId}/off-shelf")
    public ApiResponse<Map<String, Object>> offShelf(@PathVariable Long listingId) {
        return ApiResponse.ok(adminCatalogService.changeShelf(listingId, false));
    }

    @PostMapping("/recycle/listings/{listingId}/on-shelf")
    public ApiResponse<Map<String, Object>> onShelf(@PathVariable Long listingId) {
        return ApiResponse.ok(adminCatalogService.changeShelf(listingId, true));
    }

    @PostMapping("/recycle/listings/{listingId}/images")
    public ApiResponse<Map<String, Object>> attachImage(
            @PathVariable Long listingId,
            @RequestBody @Valid AttachImageRequest request
    ) {
        return ApiResponse.ok(adminCatalogService.attachListingImage(listingId, request.imageUrl()));
    }

    @GetMapping("/recycle/resale-orders")
    public ApiResponse<Map<String, Object>> pageOrders(
            @RequestParam(required = false) String payStatus,
            @RequestParam(required = false) String fulfillStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(adminCatalogService.pageOrders(payStatus, fulfillStatus, keyword, page, size));
    }

    @GetMapping("/recycle/resale-orders/{orderNo}")
    public ApiResponse<Map<String, Object>> orderDetail(@PathVariable String orderNo) {
        return ApiResponse.ok(adminCatalogService.orderDetail(orderNo));
    }

    public record AdjustPriceRequest(@NotNull @DecimalMin("0.01") BigDecimal salePrice) {
    }

    public record AttachImageRequest(@NotBlank String imageUrl) {
    }
}
