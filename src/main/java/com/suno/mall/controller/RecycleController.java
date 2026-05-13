package com.suno.mall.controller;

import com.recycle.mall.service.RecycleApplicationService;
import com.recycle.mall.common.ApiResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
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

    public RecycleController(RecycleApplicationService recycleApplicationService) {
        this.recycleApplicationService = recycleApplicationService;
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

    public record CreateRecycleOrderRequest(
            @NotNull Long userId,
            @NotBlank String snCode,
            @NotBlank String imageUrl,
            @Min(0) @Max(100) int wearScore,
            @Min(0) int recycleCount
    ) {
    }
}
