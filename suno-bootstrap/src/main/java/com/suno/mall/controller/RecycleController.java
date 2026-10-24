package com.suno.mall.controller;

import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.service.RecycleApplicationService;
import com.suno.mall.common.ApiResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/recycle")
public class RecycleController {

    private final RecycleApplicationService recycleApplicationService;
    private final AuthApplicationService authApplicationService;

    public RecycleController(
            RecycleApplicationService recycleApplicationService,
            AuthApplicationService authApplicationService
    ) {
        this.recycleApplicationService = recycleApplicationService;
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> createOrder(@RequestBody CreateRecycleOrderRequest request) {
        Map<String, Object> result = recycleApplicationService.createRecycleOrder(
                request.userId(),
                request.snCode(),
                request.imageUrl(),
                request.wearScore(),
                request.recycleCount()
        );
        return ApiResponse.ok(result);
    }

    @GetMapping("/logistics/status")
    public ApiResponse<String> queryLogisticsStatus(
            @RequestParam @NotBlank String trackingNo
    ) {
        return ApiResponse.ok(recycleApplicationService.queryLogisticsStatus(trackingNo));
    }

    @GetMapping("/orders/my")
    public ApiResponse<Map<String, Object>> listMyOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size
    ) {
        return ApiResponse.ok(recycleApplicationService.listMyRecycleOrders(
                authApplicationService.requireUserId(jwt), page, size));
    }

    @GetMapping("/points/ledger")
    public ApiResponse<Map<String, Object>> listMyPointsLedger(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size
    ) {
        return ApiResponse.ok(recycleApplicationService.listMyPointsLedger(
                authApplicationService.requireUserId(jwt), page, size));
    }

    public record CreateRecycleOrderRequest(
            @NotNull Long userId,
            @NotBlank String snCode,
            @NotBlank String imageUrl,
            @Min(0) @Max(100) int wearScore,
            @Min(0) int recycleCount
    ) {
    }
}
