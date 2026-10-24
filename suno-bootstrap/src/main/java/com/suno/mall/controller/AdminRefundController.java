package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.RefundService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * P1 管理端售后（MKT-122/123）：全量分页 + 处置（全额走整单退款，部分退款结算）。
 */
@Validated
@RestController
@RequestMapping("/api/admin/recycle/refunds")
public class AdminRefundController {

    private final RefundService refundService;

    public AdminRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> pageRefunds(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(refundService.adminPageRefunds(status, page, size));
    }

    @PostMapping("/{refundId}/process")
    public ApiResponse<Map<String, Object>> processRefund(@PathVariable Long refundId) {
        return ApiResponse.ok(refundService.processRefund(refundId));
    }
}
