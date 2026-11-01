package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.service.RecycleCartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 回收购物车：扫码估价添加旧物（书籍 ISBN / 多品类手动填写），
 * 连续添加 → 一次性提交为多笔回收单。
 */
@Validated
@RestController
@RequestMapping("/api/recycle")
public class RecycleCartController {

    private final RecycleCartService recycleCartService;
    private final AuthApplicationService authApplicationService;

    public RecycleCartController(RecycleCartService recycleCartService,
                                 AuthApplicationService authApplicationService) {
        this.recycleCartService = recycleCartService;
        this.authApplicationService = authApplicationService;
    }

    /** 扫码估价添加：ISBN → Open Library → 估价 → 回收车 */
    @PostMapping("/scan")
    public ApiResponse<Map<String, Object>> scan(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid ScanRequest request
    ) {
        int wearScore = request.wearScore() != null ? request.wearScore() : 85;
        return ApiResponse.ok(recycleCartService.scanAndAdd(
                authApplicationService.requireUserId(jwt), request.isbn(), wearScore));
    }

    /** 手动添加：非书籍类旧物（衣物/家居/美妆等） */
    @PostMapping("/cart")
    public ApiResponse<Map<String, Object>> addManual(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid ManualAddRequest request
    ) {
        return ApiResponse.ok(recycleCartService.addManualItem(
                authApplicationService.requireUserId(jwt),
                request.title(), request.category(), request.wearScore(), request.estimatedPrice()));
    }

    /** 查看回收车 */
    @GetMapping("/cart")
    public ApiResponse<List<Map<String, Object>>> listCart(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(recycleCartService.listCart(authApplicationService.requireUserId(jwt)));
    }

    /** 从回收车移除 */
    @DeleteMapping("/cart/{itemId}")
    public ApiResponse<Map<String, Object>> removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long itemId
    ) {
        recycleCartService.removeItem(authApplicationService.requireUserId(jwt), itemId);
        return ApiResponse.ok(Map.of("removed", true));
    }

    /** 提交回收车：每件 → 商品 + 回收单 + 积分 + 通知 */
    @PostMapping("/cart/submit")
    public ApiResponse<Map<String, Object>> submit(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(recycleCartService.submitCart(authApplicationService.requireUserId(jwt)));
    }

    public record ScanRequest(@NotBlank String isbn, Integer wearScore) {
    }

    public record ManualAddRequest(@NotBlank String title, String category,
                                   @NotNull Integer wearScore, BigDecimal estimatedPrice) {
    }
}
