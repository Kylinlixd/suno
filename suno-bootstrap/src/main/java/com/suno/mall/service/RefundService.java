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
import org.springframework.transaction.annotation.Transactional;

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

    public RefundService(RefundOrderRepository refundOrderRepository,
                         ResaleOrderRepository resaleOrderRepository,
                         NotificationService notificationService) {
        this.refundOrderRepository = refundOrderRepository;
        this.resaleOrderRepository = resaleOrderRepository;
        this.notificationService = notificationService;
    }

    @ResaleTransactional
    public Map<String, Object> applyRefund(String orderNo, Long buyerUserId, String reason) {
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
        RefundOrderEntity refund = new RefundOrderEntity();
        refund.setRefundNo("RFD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        refund.setResaleOrderId(order.getId());
        refund.setBuyerUserId(buyerUserId);
        refund.setAmount(payableAmountOf(order));
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

    /** 退款额 = 应付金额（扣除优惠券抵扣后的实付） */
    static java.math.BigDecimal payableAmountOf(ResaleOrderEntity order) {
        java.math.BigDecimal payable = order.getPayableAmount();
        return payable != null ? payable : order.getAmount();
    }
}
