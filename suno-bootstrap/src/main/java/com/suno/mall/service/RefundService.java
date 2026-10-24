package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.config.ResaleTransactional;
import com.suno.mall.entity.RefundOrderEntity;
import com.suno.mall.entity.ResaleOrderEntity;
import com.suno.mall.dao.RefundOrderRepository;
import com.suno.mall.dao.ResaleOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M2 用户端售后。买家对已支付订单发起退款申请（同一订单同时只允许一个未完结售后单），
 * 管理端沿用既有退款处置（ResaleOrderService#refundPaidResaleOrder）处理 PENDING 单。
 */
@Service
public class RefundService {

    private static final String REFUND_STATUS_PENDING = "PENDING";
    private static final String REFUND_STATUS_CANCELLED = "CANCELLED";
    private static final List<String> OPEN_STATUSES = List.of("PENDING", "PROCESSING");

    private final RefundOrderRepository refundOrderRepository;
    private final ResaleOrderRepository resaleOrderRepository;
    private final NotificationService notificationService;
    private final ResaleOrderService resaleOrderService;
    private final AuditLogService auditLogService;
    private final com.suno.mall.payment.AlipayProperties alipayProperties;
    private final com.suno.mall.payment.AlipayOpenApiClient alipayOpenApiClient;

    public RefundService(RefundOrderRepository refundOrderRepository,
                         ResaleOrderRepository resaleOrderRepository,
                         NotificationService notificationService,
                         ResaleOrderService resaleOrderService,
                         AuditLogService auditLogService,
                         com.suno.mall.payment.AlipayProperties alipayProperties,
                         com.suno.mall.payment.AlipayOpenApiClient alipayOpenApiClient) {
        this.refundOrderRepository = refundOrderRepository;
        this.resaleOrderRepository = resaleOrderRepository;
        this.notificationService = notificationService;
        this.resaleOrderService = resaleOrderService;
        this.auditLogService = auditLogService;
        this.alipayProperties = alipayProperties;
        this.alipayOpenApiClient = alipayOpenApiClient;
    }

    @ResaleTransactional
    public Map<String, Object> applyRefund(String orderNo, Long buyerUserId, String reason) {
        return applyRefund(orderNo, buyerUserId, reason, null);
    }

    /** P1 支持部分退款：amount 为空表示全额；不可超过订单应付金额 */
    @ResaleTransactional
    public Map<String, Object> applyRefund(String orderNo, Long buyerUserId, String reason, @Nullable BigDecimal amount) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyerUser().getId().equals(buyerUserId)) {
            throw new BizException("仅允许对本人订单申请售后", ErrorCode.ORDER_NOT_OWNER);
        }
        if (!"PAID".equals(order.getPayStatus())) {
            throw new BizException("仅已支付订单可申请售后", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if ("REFUNDED".equals(order.getFulfillStatus())) {
            throw new BizException("订单已退款", ErrorCode.REFUND_STATUS_CONFLICT);
        }
        if (refundOrderRepository.existsByResaleOrderIdAndStatusIn(order.getId(), OPEN_STATUSES)) {
            throw new BizException("该订单已有进行中的售后申请", ErrorCode.REFUND_ALREADY_OPEN);
        }
        if (reason == null || reason.isBlank() || reason.trim().length() < 5) {
            throw new BizException("请填写至少 5 个字的售后原因", ErrorCode.PARAM_INVALID);
        }
        BigDecimal refundAmount = amount == null ? payableAmountOf(order) : amount;
        if (refundAmount.compareTo(new java.math.BigDecimal("0.01")) < 0
                || refundAmount.compareTo(payableAmountOf(order)) > 0) {
            throw new BizException("退款金额必须在 0.01 与订单应付金额之间", ErrorCode.PARAM_INVALID);
        }
        RefundOrderEntity refund = new RefundOrderEntity();
        refund.setRefundNo("RFD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        refund.setResaleOrderId(order.getId());
        refund.setBuyerUserId(buyerUserId);
        refund.setAmount(refundAmount);
        refund.setReason(reason.trim());
        refund.setStatus(REFUND_STATUS_PENDING);
        refundOrderRepository.save(refund);
        notificationService.push(buyerUserId, "REFUND_APPLIED", "售后申请已提交",
                "订单 " + orderNo + " 的售后申请已提交，我们会尽快处理。", refund.getId(), "REFUND_ORDER");
        return Map.of("refundNo", refund.getRefundNo(), "orderNo", orderNo,
                "status", refund.getStatus(), "amount", refund.getAmount());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> listMyRefunds(Long buyerUserId, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        var result = refundOrderRepository.findByBuyerUserIdOrderByCreatedAtDesc(buyerUserId,
                PageRequest.of(safePage, safeSize));
        List<Map<String, Object>> content = result.getContent().stream().map(refund -> {
            Map<String, Object> row = new HashMap<>();
            row.put("refundId", refund.getId());
            row.put("refundNo", refund.getRefundNo());
            row.put("amount", refund.getAmount());
            row.put("reason", refund.getReason());
            row.put("status", refund.getStatus());
            row.put("createdAt", refund.getCreatedAt());
            row.put("processedAt", refund.getProcessedAt());
            var order = resaleOrderRepository.findById(refund.getResaleOrderId());
            order.ifPresent(value -> row.put("orderNo", value.getOrderNo()));
            return row;
        }).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("totalElements", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        payload.put("page", safePage);
        payload.put("size", safeSize);
        return payload;
    }

    @ResaleTransactional
    public Map<String, Object> cancelRefund(Long refundId, Long buyerUserId) {
        RefundOrderEntity refund = refundOrderRepository.findByIdAndBuyerUserId(refundId, buyerUserId)
                .orElseThrow(() -> new BizException("售后单不存在", ErrorCode.REFUND_NOT_FOUND));
        if (!REFUND_STATUS_PENDING.equals(refund.getStatus())) {
            throw new BizException("仅待处理售后单可撤销", ErrorCode.REFUND_STATUS_CONFLICT);
        }
        refund.setStatus(REFUND_STATUS_CANCELLED);
        refund.setProcessedAt(LocalDateTime.now());
        refundOrderRepository.save(refund);
        return Map.of("refundId", refundId, "status", refund.getStatus());
    }

    // ==================== P1 管理端 ====================

    /** 管理端全量售后分页（可选状态过滤） */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> adminPageRefunds(String status, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        var all = refundOrderRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        var filtered = all.stream()
                .filter(refund -> status == null || status.isBlank() || status.equals(refund.getStatus()))
                .toList();
        var content = filtered.stream().skip((long) safePage * safeSize).limit(safeSize).map(refund -> {
            Map<String, Object> row = new HashMap<>();
            row.put("refundId", refund.getId());
            row.put("refundNo", refund.getRefundNo());
            row.put("amount", refund.getAmount());
            row.put("reason", refund.getReason());
            row.put("status", refund.getStatus());
            row.put("createdAt", refund.getCreatedAt());
            row.put("processedAt", refund.getProcessedAt());
            row.put("buyerUserId", refund.getBuyerUserId());
            resaleOrderRepository.findById(refund.getResaleOrderId()).ifPresent(order -> {
                row.put("orderNo", order.getOrderNo());
                row.put("payChannel", order.getPayChannel());
                row.put("tradeNo", order.getTradeNo());
                row.put("payableAmount", payableAmountOf(order));
            });
            return row;
        }).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("totalElements", filtered.size());
        payload.put("totalPages", (filtered.size() + safeSize - 1) / safeSize);
        payload.put("page", safePage);
        payload.put("size", safeSize);
        return payload;
    }

    /**
     * 管理端处置售后申请：全额退款走既有整单退款状态机（回库存/退积分）；
     * 部分退款结算退款单但订单保持 PAID（渠道打款由支付宝适配层或线下完成）。
     */
    @ResaleTransactional
    public Map<String, Object> processRefund(Long refundId) {
        RefundOrderEntity refund = refundOrderRepository.findById(refundId)
                .orElseThrow(() -> new BizException("售后单不存在", ErrorCode.REFUND_NOT_FOUND));
        if (!"PENDING".equals(refund.getStatus())) {
            throw new BizException("仅待处理售后单可处置", ErrorCode.REFUND_STATUS_CONFLICT);
        }
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(
                        resaleOrderRepository.findById(refund.getResaleOrderId())
                                .orElseThrow(() -> new BizException("订单不存在", ErrorCode.ORDER_NOT_FOUND)).getOrderNo())
                .orElseThrow(() -> new BizException("订单不存在", ErrorCode.ORDER_NOT_FOUND));
        boolean full = refund.getAmount().compareTo(payableAmountOf(order)) >= 0;
        if (full) {
            // 复用整单退款：内部会结案 PENDING 售后单、回库存、退积分并通知
            resaleOrderService.refundPaidResaleOrder(order.getOrderNo(), new com.suno.mall.service.support.AuditContext(null, null));
            refund = refundOrderRepository.findById(refundId).orElse(refund);
            return Map.of("refundId", refundId, "status", refund.getStatus(), "mode", "FULL");
        }
        String channel = "ALIPAY".equals(order.getPayChannel()) ? "ALIPAY" : "ORIGINAL";
        if ("ALIPAY".equals(channel) && alipayProperties.isEnabled()) {
            try {
                alipayOpenApiClient.tradeRefund(order.getOrderNo(), refund.getRefundNo(), refund.getAmount());
            } catch (Exception failure) {
                throw new BizException("支付宝退款失败: " + failure.getMessage(), ErrorCode.PAYMENT_DATA_CONFLICT);
            }
        }
        refund.setStatus("PROCESSED");
        refund.setProcessedAt(LocalDateTime.now());
        refund.setRefundChannel(channel);
        refundOrderRepository.save(refund);
        notificationService.push(refund.getBuyerUserId(), "REFUND_PROCESSED", "部分退款已完成",
                "订单 " + order.getOrderNo() + " 的部分退款 ¥" + refund.getAmount() + " 已处理，请留意到账。",
                refund.getId(), "REFUND_ORDER");
        auditLogService.logAction("REFUND_PARTIAL_PROCESS", "REFUND_ORDER", refund.getRefundNo(),
                "amount=" + refund.getAmount() + ",orderNo=" + order.getOrderNo());
        return Map.of("refundId", refundId, "status", "PROCESSED", "mode", "PARTIAL");
    }

    /** 管理端退款明细 CSV 导出（可选状态过滤） */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 60)
    public String exportRefundsCsv(String status) {
        var rows = refundOrderRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).stream()
                .filter(refund -> status == null || status.isBlank() || status.equals(refund.getStatus()))
                .toList();
        StringBuilder csv = new StringBuilder("refund_no,order_no,amount,status,channel,created_at,processed_at\n");
        for (var refund : rows) {
            String orderNo = resaleOrderRepository.findById(refund.getResaleOrderId())
                    .map(order -> order.getOrderNo()).orElse("-");
            csv.append(refund.getRefundNo()).append(',')
                    .append(orderNo).append(',')
                    .append(refund.getAmount()).append(',')
                    .append(refund.getStatus()).append(',')
                    .append(refund.getRefundChannel() == null ? "-" : refund.getRefundChannel()).append(',')
                    .append(refund.getCreatedAt()).append(',')
                    .append(refund.getProcessedAt() == null ? "-" : refund.getProcessedAt()).append('\n');
        }
        return csv.toString();
    }

    /** 退款额 = 应付金额（扣除优惠券与积分抵扣后的实付） */
    static java.math.BigDecimal payableAmountOf(ResaleOrderEntity order) {
        java.math.BigDecimal payable = order.getPayableAmount();
        return payable != null ? payable : order.getAmount();
    }
}
