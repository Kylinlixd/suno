
package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.Constants;
import com.suno.mall.common.ErrorCode;
import org.jspecify.annotations.Nullable;
import com.suno.mall.service.support.AuditContext;
import com.suno.mall.service.support.I18nHelper;
import com.suno.mall.entity.PaymentIdempotencyEntity;
import com.suno.mall.entity.ProductEntity;
import com.suno.mall.entity.ResaleListingEntity;
import com.suno.mall.entity.ResaleOrderEntity;
import com.suno.mall.entity.ResaleReviewEntity;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.dao.PaymentIdempotencyRepository;
import com.suno.mall.dao.RefundOrderRepository;
import com.suno.mall.dao.ResaleListingRepository;
import com.suno.mall.dao.ResaleOrderRepository;
import com.suno.mall.dao.ResaleReviewRepository;
import com.suno.mall.dao.UserAccountRepository;
import com.suno.mall.dao.UserAddressRepository;
import com.suno.mall.entity.RefundOrderEntity;
import com.suno.mall.entity.UserAddressEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import com.suno.mall.config.ResaleTransactional;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 二销订单服务
 */
@Service
public class ResaleOrderService {

    // 使用公共常量替代重复定义
    private static final String LISTING_STATUS_ON_SHELF = Constants.LISTING_STATUS_ON_SHELF;
    private static final String LISTING_STATUS_SOLD_OUT = Constants.LISTING_STATUS_SOLD_OUT;

    @Value("${mall.review.append-window-days:" + Constants.DEFAULT_REVIEW_APPEND_WINDOW_DAYS + "}")
    private int reviewAppendWindowDays;

    private final UserAccountRepository userAccountRepository;
    private final ResaleOrderRepository resaleOrderRepository;
    private final ResaleReviewRepository resaleReviewRepository;
    private final ResaleListingRepository resaleListingRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final UserAddressRepository userAddressRepository;
    private final RefundOrderRepository refundOrderRepository;
    private final CouponService couponService;
    private final CartService cartService;
    private final NotificationService notificationService;
    private final com.suno.mall.dao.PointsLedgerRepository pointsLedgerRepository;
    private final MemberLevelService memberLevelService;
    private final com.suno.mall.core.event.EventOutbox eventOutbox;
    private final AuditLogService auditLogService;

    public ResaleOrderService(
            UserAccountRepository userAccountRepository,
            ResaleOrderRepository resaleOrderRepository,
            ResaleReviewRepository resaleReviewRepository,
            ResaleListingRepository resaleListingRepository,
            PaymentIdempotencyRepository paymentIdempotencyRepository,
            UserAddressRepository userAddressRepository,
            RefundOrderRepository refundOrderRepository,
            CouponService couponService,
            CartService cartService,
            NotificationService notificationService,
            com.suno.mall.dao.PointsLedgerRepository pointsLedgerRepository,
            MemberLevelService memberLevelService,
            com.suno.mall.core.event.EventOutbox eventOutbox,
            AuditLogService auditLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.resaleOrderRepository = resaleOrderRepository;
        this.resaleReviewRepository = resaleReviewRepository;
        this.resaleListingRepository = resaleListingRepository;
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
        this.userAddressRepository = userAddressRepository;
        this.refundOrderRepository = refundOrderRepository;
        this.couponService = couponService;
        this.cartService = cartService;
        this.notificationService = notificationService;
        this.pointsLedgerRepository = pointsLedgerRepository;
        this.memberLevelService = memberLevelService;
        this.eventOutbox = eventOutbox;
        this.auditLogService = auditLogService;
    }

    @ResaleTransactional
    @CacheEvict(value = "resaleOrder", allEntries = true)
    public Map<String, Object> createResaleOrder(Long buyerUserId, Long listingId) {
        return createResaleOrder(buyerUserId, listingId, null, null, false);
    }

    public Map<String, Object> createResaleOrder(Long buyerUserId, Long listingId, Long addressId, Long couponUserId) {
        return createResaleOrder(buyerUserId, listingId, addressId, couponUserId, false);
    }

    /**
     * M2 完整下单：可选收货地址（落快照）与优惠券（同事务核销）。
     * addressId 为空时订单不携带收货信息（兼容旧调用），前端结算流程必须传地址。
     */
    @ResaleTransactional
    @CacheEvict(value = "resaleOrder", allEntries = true)
    public Map<String, Object> createResaleOrder(Long buyerUserId, Long listingId,
                                                 @Nullable Long addressId, @Nullable Long couponUserId, boolean usePoints) {
        UserAccountEntity buyer = userAccountRepository.findById(buyerUserId)
                .orElseThrow(() -> new BizException("买家不存在: " + buyerUserId, ErrorCode.ORDER_NOT_FOUND));
        ResaleListingEntity listing = resaleListingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> new BizException("商品不存在: " + listingId, ErrorCode.ORDER_NOT_FOUND));
        if (!LISTING_STATUS_ON_SHELF.equals(listing.getStatus())) {
            throw new BizException("商品已下架或售罄", ErrorCode.ORDER_LISTING_UNAVAILABLE);
        }
        if (listing.getStock() <= 0) {
            throw new BizException("库存不足", ErrorCode.ORDER_STOCK_INSUFFICIENT);
        }
        int reserved = resaleListingRepository.reserveOneStock(
                listingId, LISTING_STATUS_ON_SHELF, LISTING_STATUS_SOLD_OUT, LocalDateTime.now());
        if (reserved == 0) {
            // 读取后库存被并发抢占或商品下架
            throw new BizException("商品已下架或库存不足", ErrorCode.ORDER_CONCURRENT_CONFLICT);
        }

        String orderNo = "B2C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ResaleOrderEntity order = new ResaleOrderEntity();
        order.setOrderNo(orderNo);
        order.setBuyerUser(buyer);
        order.setListing(listing);
        order.setAmount(listing.getSalePrice());
        order.setPayStatus("UNPAID");
        order.setFulfillStatus("WAIT_PAY");
        order.setCreatedAt(LocalDateTime.now());
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayableAmount(order.getAmount());
        if (addressId != null) {
            UserAddressEntity address = userAddressRepository.findByIdAndUser_Id(addressId, buyerUserId)
                    .orElseThrow(() -> new BizException("收货地址不存在", ErrorCode.ADDRESS_NOT_FOUND));
            order.setReceiverName(address.getReceiverName());
            order.setReceiverPhone(address.getReceiverPhone());
            order.setReceiverAddress(String.join(" ", address.getProvince(), address.getCity(),
                    address.getDistrict(), address.getDetailAddress()));
        }
        resaleOrderRepository.save(order);
        if (couponUserId != null) {
            // 与下单同一事务核销；门槛不足/券不可用时整个下单回滚
            Map<String, Object> redeemed = couponService.redeem(buyerUserId, couponUserId, order.getAmount(), order.getId());
            order.setCouponUserId(couponUserId);
            order.setDiscountAmount((BigDecimal) redeemed.get("discountAmount"));
            order.setPayableAmount((BigDecimal) redeemed.get("payableAmount"));
            resaleOrderRepository.save(order);
        }
        if (usePoints) {
            applyPointsDeduction(buyer, order);
        }
        cartService.removeListingFromCart(buyerUserId, listingId);
        auditLogService.logAction("RESALE_ORDER_CREATE", "RESALE_ORDER", orderNo,
                "listingId=" + listingId + ",buyerUserId=" + buyerUserId
                        + ",discountAmount=" + order.getDiscountAmount()
                        + ",pointsDeductAmount=" + order.getPointsDeductAmount());
        notificationService.push(buyerUserId, "ORDER_CREATED", "订单已创建",
                "订单 " + orderNo + " 已创建，应付 ¥" + order.getPayableAmount() + "，请尽快完成支付。",
                order.getId(), "RESALE_ORDER");
        eventOutbox.publish(new com.suno.mall.marketplace.api.event.MarketplaceOrderCreated(
                orderNo, buyerUserId, String.valueOf(order.getPayableAmount())));
        eventOutbox.publish(new com.suno.mall.marketplace.api.event.MarketplaceStockReserved(
                orderNo, listingId, 1));

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("listingId", listing.getId());
        result.put("amount", order.getAmount());
        result.put("discountAmount", order.getDiscountAmount());
        result.put("payableAmount", order.getPayableAmount());
        result.put("pointsUsed", order.getPointsUsed());
        result.put("pointsDeductAmount", order.getPointsDeductAmount());
        result.put("payStatus", order.getPayStatus());
        result.put("fulfillStatus", order.getFulfillStatus());
        return result;
    }

    /** P1 积分抵现：100 积分 = ¥1，最多抵扣应付的 20%，消耗需为 100 的整数倍；同事务写积分流水。 */
    private void applyPointsDeduction(UserAccountEntity buyer, ResaleOrderEntity order) {
        int pointsPerYuan = 100;
        var cap = order.getPayableAmount()
                .multiply(new BigDecimal("0.20"))
                .setScale(0, java.math.RoundingMode.DOWN);
        int maxPoints = cap.intValue() * pointsPerYuan;
        int usable = Math.min(buyer.getPoints() == null ? 0 : buyer.getPoints(), maxPoints);
        usable = usable / pointsPerYuan * pointsPerYuan;
        if (usable < pointsPerYuan) {
            return;
        }
        BigDecimal deduct = BigDecimal.valueOf(usable / pointsPerYuan).setScale(2);
        if (deduct.compareTo(order.getPayableAmount()) >= 0) {
            return;
        }
        buyer.setPoints(buyer.getPoints() - usable);
        userAccountRepository.save(buyer);
        order.setPointsUsed(usable);
        order.setPointsDeductAmount(deduct);
        order.setPayableAmount(order.getPayableAmount().subtract(deduct));
        resaleOrderRepository.save(order);
        writePointsLedger(buyer, -usable, "ORDER_USE_POINTS:" + order.getOrderNo());
    }

    /** 取消/退款时按订单积分占用回退（幂等：仅回退一次由订单状态机保证） */
    private void returnOrderPoints(ResaleOrderEntity order) {
        if (order.getPointsUsed() == null || order.getPointsUsed() <= 0) {
            return;
        }
        UserAccountEntity buyer = order.getBuyerUser();
        buyer.setPoints((buyer.getPoints() == null ? 0 : buyer.getPoints()) + order.getPointsUsed());
        userAccountRepository.save(buyer);
        writePointsLedger(buyer, order.getPointsUsed(), "ORDER_RETURN_POINTS:" + order.getOrderNo());
    }

    private void writePointsLedger(UserAccountEntity user, int delta, String reason) {
        com.suno.mall.entity.PointsLedgerEntity ledger = new com.suno.mall.entity.PointsLedgerEntity();
        ledger.setUser(user);
        ledger.setPointsDelta(delta);
        ledger.setReason(reason);
        ledger.setCreatedAt(LocalDateTime.now());
        pointsLedgerRepository.save(ledger);
        memberLevelService.recalculate(user);
    }


    @ResaleTransactional
    @CacheEvict(value = "resaleOrder", allEntries = true)
    public Map<String, Object> markResaleOrderPaid(String orderNo) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!"UNPAID".equals(order.getPayStatus())) {
            throw new BizException("订单支付状态异常: " + order.getPayStatus(), ErrorCode.ORDER_STATUS_CONFLICT);
        }
        int updated = resaleOrderRepository.compareAndSetStatus(
                orderNo, "UNPAID", "WAIT_PAY", "PAID", "TO_DELIVER");
        if (updated == 0) {
            // 自动关单或用户取消已先一步改变状态，迟到支付必须拒绝
            throw new BizException("订单状态已变更，不可支付", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        auditLogService.logAction("RESALE_ORDER_PAY", "RESALE_ORDER", orderNo, "payStatus=PAID");
        notificationService.push(order.getBuyerUser().getId(), "ORDER_PAID", "支付成功",
                "订单 " + orderNo + " 已支付，我们会尽快为你安排发货。", order.getId(), "RESALE_ORDER");
        return Map.of("orderNo", orderNo, "payStatus", "PAID", "fulfillStatus", "TO_DELIVER");
    }

    /** M3 供网关通知校验金额：不含买家信息（通知来自支付宝，无 JWT） */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> getOrderAmountForGateway(String orderNo) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        Map<String, Object> result = new HashMap<>();
        result.put("amount", order.getAmount());
        result.put("payableAmount", order.getPayableAmount() != null ? order.getPayableAmount() : order.getAmount());
        result.put("payStatus", order.getPayStatus());
        return result;
    }

    /** PAY-S005 候选订单：超过 staleMinutes 仍 UNPAID/WAIT_PAY 的订单摘要 */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public java.util.List<Map<String, Object>> findStaleUnpaidOrders(int staleMinutes, int limit) {
        return resaleOrderRepository.findByPayStatusAndFulfillStatusAndCreatedAtBefore(
                        "UNPAID", "WAIT_PAY", LocalDateTime.now().minusMinutes(staleMinutes),
                        PageRequest.of(0, limit)).stream()
                .map(order -> Map.<String, Object>of("orderNo", order.getOrderNo()))
                .toList();
    }

    /** M3 真实网关支付成功后回填渠道信息（幂等，仅 PAID 订单） */
    @ResaleTransactional
    public void attachPaymentChannel(String orderNo, String payChannel, String tradeNo) {
        resaleOrderRepository.attachChannelInfo(orderNo, payChannel, tradeNo, LocalDateTime.now());
    }

    @ResaleTransactional
    public Map<String, Object> markResaleOrderPaidWithIdempotency(String orderNo, String idempotencyKey) {
        PaymentIdempotencyEntity existing = paymentIdempotencyRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getOrderNo().equals(orderNo)) {
                throw new BizException("幂等键已用于其他订单", ErrorCode.ORDER_IDEMPOTENT_KEY_CONFLICT);
            }
            return Map.of("orderNo", existing.getOrderNo(), "payStatus", existing.getPayStatusSnapshot(), "idempotentReplay", true);
        }
        Map<String, Object> paidResult = markResaleOrderPaid(orderNo);
        PaymentIdempotencyEntity record = new PaymentIdempotencyEntity();
        record.setIdempotencyKey(idempotencyKey);
        record.setOrderNo(orderNo);
        record.setPayStatusSnapshot(String.valueOf(paidResult.get("payStatus")));
        record.setCreatedAt(LocalDateTime.now());
        paymentIdempotencyRepository.save(record);
        eventOutbox.publish(new com.suno.mall.payment.api.event.PaymentApplied(
                orderNo, idempotencyKey, String.valueOf(paidResult.get("payStatus"))));
        eventOutbox.publish(new com.suno.mall.marketplace.api.event.MarketplacePaymentAccepted(
                orderNo, idempotencyKey));
        return Map.of("orderNo", paidResult.get("orderNo"), "payStatus", paidResult.get("payStatus"),
                "fulfillStatus", paidResult.get("fulfillStatus"), "idempotentReplay", false);
    }

    @ResaleTransactional
    @CacheEvict(value = "resaleOrder", allEntries = true)
    public Map<String, Object> deliverResaleOrder(String orderNo, AuditContext auditContext) {
        return deliverResaleOrder(orderNo, null, auditContext);
    }

    public Map<String, Object> deliverResaleOrder(String orderNo, @Nullable String trackingNo, AuditContext auditContext) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!"PAID".equals(order.getPayStatus())) {
            throw new BizException("未支付订单不可发货", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if ("DELIVERED".equals(order.getFulfillStatus())) {
            return Map.of("orderNo", orderNo, "payStatus", "PAID", "fulfillStatus", "DELIVERED", "idempotentReplay", true);
        }
        int updated = resaleOrderRepository.compareAndSetStatus(
                orderNo, "PAID", "TO_DELIVER", "PAID", "DELIVERED");
        if (updated == 0) {
            throw new BizException("当前履约状态不可发货", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if (trackingNo != null && !trackingNo.isBlank()) {
            order.setTrackingNo(trackingNo.trim());
            resaleOrderRepository.save(order);
        }
        auditLogService.logAction("RESALE_ORDER_DELIVER", "RESALE_ORDER", orderNo,
                "fulfillStatus=DELIVERED" + auditLogService.buildAuditContextSuffix(auditContext));
        notificationService.push(order.getBuyerUser().getId(), "ORDER_DELIVERED", "商品已发货",
                "订单 " + orderNo + " 已发货" + (order.getTrackingNo() == null ? "" : "（物流单号 " + order.getTrackingNo() + "）") + "，请注意查收。",
                order.getId(), "RESALE_ORDER", order.getBuyerUser().getUsername());
        return Map.of("orderNo", orderNo, "payStatus", "PAID", "fulfillStatus", "DELIVERED");
    }

    @ResaleTransactional
    @CacheEvict(value = "resaleOrder", allEntries = true)
    public Map<String, Object> confirmResaleOrderReceipt(String orderNo, Long buyerUserId) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyerUser().getId().equals(buyerUserId)) {
            throw new BizException("仅允许确认本人订单收货", ErrorCode.ORDER_NOT_OWNER);
        }
        if (!"PAID".equals(order.getPayStatus())) {
            throw new BizException("未支付订单不可确认收货", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if ("COMPLETED".equals(order.getFulfillStatus())) {
            throw new BizException("订单已确认收货", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if (!"DELIVERED".equals(order.getFulfillStatus())) {
            throw new BizException("当前履约状态不可确认收货: " + order.getFulfillStatus(), ErrorCode.ORDER_STATUS_CONFLICT);
        }
        int updated = resaleOrderRepository.compareAndSetStatus(
                orderNo, "PAID", "DELIVERED", "PAID", "COMPLETED");
        if (updated == 0) {
            throw new BizException("订单状态已变更，请刷新后重试", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        auditLogService.logAction("RESALE_ORDER_RECEIVE", "RESALE_ORDER", orderNo, "fulfillStatus=COMPLETED,buyerUserId=" + buyerUserId);
        notificationService.push(buyerUserId, "ORDER_COMPLETED", "订单已完成",
                "订单 " + orderNo + " 已确认收货，欢迎评价这次的循环之旅。", order.getId(), "RESALE_ORDER");
        eventOutbox.publish(new com.suno.mall.marketplace.api.event.MarketplaceFulfillmentCompleted(
                orderNo, buyerUserId));
        return Map.of("orderNo", orderNo, "buyerUserId", buyerUserId, "payStatus", "PAID", "fulfillStatus", "COMPLETED");
    }

    @ResaleTransactional
    @CacheEvict(value = "resaleOrder", allEntries = true)
    public Map<String, Object> cancelUnpaidResaleOrder(String orderNo, Long buyerUserId) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyerUser().getId().equals(buyerUserId)) {
            throw new BizException("仅允许取消本人订单", ErrorCode.ORDER_NOT_OWNER);
        }
        if (!"UNPAID".equals(order.getPayStatus())) {
            throw new BizException("仅 UNPAID 订单可取消", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if (!"WAIT_PAY".equals(order.getFulfillStatus())) {
            throw new BizException("当前履约状态不可取消: " + order.getFulfillStatus(), ErrorCode.ORDER_STATUS_CONFLICT);
        }
        int updated = resaleOrderRepository.compareAndSetStatus(
                orderNo, "UNPAID", "WAIT_PAY", "UNPAID", "CANCELLED");
        if (updated == 0) {
            // 自动关单或支付先一步成功；只有 CAS 成功的事务可以释放库存
            throw new BizException("订单状态已变更，取消失败", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        releaseListingStock(order.getListing().getId());
        couponService.releaseForOrder(order.getId());
        returnOrderPoints(order);
        auditLogService.logAction("RESALE_ORDER_CANCEL", "RESALE_ORDER", orderNo, "fulfillStatus=CANCELLED,buyerUserId=" + buyerUserId);
        notificationService.push(buyerUserId, "ORDER_CANCELLED", "订单已取消",
                "订单 " + orderNo + " 已取消，库存与优惠券已退回。", order.getId(), "RESALE_ORDER");
        eventOutbox.publish(new com.suno.mall.marketplace.api.event.MarketplaceStockReleased(
                orderNo, order.getListing().getId(), "USER_CANCEL"));
        return Map.of("orderNo", orderNo, "payStatus", "UNPAID", "fulfillStatus", "CANCELLED");
    }

    @ResaleTransactional
    @CacheEvict(value = "resaleOrder", allEntries = true)
    public Map<String, Object> refundPaidResaleOrder(String orderNo, AuditContext auditContext) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!"PAID".equals(order.getPayStatus())) {
            throw new BizException("仅 PAID 订单可退款", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if ("REFUNDED".equals(order.getFulfillStatus())) {
            throw new BizException("订单已退款", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        int updated = resaleOrderRepository.refundIfPaid(orderNo);
        if (updated == 0) {
            throw new BizException("订单状态已变更，退款失败", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        releaseListingStock(order.getListing().getId());
        returnOrderPoints(order);
        // 买家发起的 PENDING 售后单随退款完成一并结案
        refundOrderRepository.findFirstByResaleOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), "PENDING")
                .ifPresent(refund -> {
                    refund.setStatus("PROCESSED");
                    refund.setProcessedAt(LocalDateTime.now());
                    refund.setRefundChannel("ORIGINAL");
                    refundOrderRepository.save(refund);
                });
        auditLogService.logAction("RESALE_ORDER_REFUND", "RESALE_ORDER", orderNo,
                "payStatus=REFUNDED" + auditLogService.buildAuditContextSuffix(auditContext));
        notificationService.push(order.getBuyerUser().getId(), "ORDER_REFUNDED", "退款已到账",
                "订单 " + orderNo + " 已退款 ¥" + order.getPayableAmount() + "，请留意到账信息。",
                order.getId(), "RESALE_ORDER", order.getBuyerUser().getUsername());
        return Map.of("orderNo", orderNo, "payStatus", "REFUNDED", "fulfillStatus", "REFUNDED");
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> queryResaleOrderTrack(String orderNo, Long buyerUserId) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyerUser().getId().equals(buyerUserId)) {
            throw new BizException("仅允许查询本人订单履约轨迹", ErrorCode.ORDER_NOT_OWNER);
        }
        List<Map<String, Object>> events = auditLogService.listAuditLogs(null, orderNo, 200).stream()
                .filter(e -> "RESALE_ORDER".equals(e.get("targetType")))
                .sorted(Comparator.comparing(e -> (LocalDateTime) e.get("createdAt")))
                .map(log -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("actionType", log.get("actionType"));
                    event.put("detail", log.get("detail") == null ? "" : log.get("detail"));
                    event.put("createdAt", log.get("createdAt"));
                    return event;
                }).toList();

        ResaleReviewEntity review = resaleReviewRepository.findByOrder_OrderNoAndUser_Id(orderNo, buyerUserId).orElse(null);
        boolean hasReviewed = review != null;
        boolean canCreateReview = "COMPLETED".equals(order.getFulfillStatus()) && !hasReviewed;
        LocalDateTime completedAt = auditLogService.resolveOrderCompletedAt(orderNo);
        LocalDateTime appendDeadline = completedAt == null ? null : completedAt.plusDays(Math.max(1, reviewAppendWindowDays));
        boolean withinAppendWindow = appendDeadline != null && !appendDeadline.isBefore(LocalDateTime.now());
        boolean canAppendReview = hasReviewed
                && (review.getAppendContent() == null || review.getAppendContent().isBlank())
                && "COMPLETED".equals(order.getFulfillStatus())
                && withinAppendWindow;
        long appendRemainingHours = appendDeadline == null ? 0L : Math.max(0L, Duration.between(LocalDateTime.now(), appendDeadline).toHours());
        long appendRemainingDays = appendRemainingHours / 24;

        Map<String, Object> reviewEligibility = new HashMap<>();
        reviewEligibility.put("hasReviewed", hasReviewed);
        reviewEligibility.put("canCreateReview", canCreateReview);
        reviewEligibility.put("canAppendReview", canAppendReview);
        reviewEligibility.put("appendWindowDays", reviewAppendWindowDays);
        reviewEligibility.put("appendDeadline", appendDeadline);
        reviewEligibility.put("appendRemainingHours", appendRemainingHours);
        reviewEligibility.put("appendRemainingDays", appendRemainingDays);
        reviewEligibility.put("appendRemainingText", I18nHelper.appendRemainingText(appendRemainingHours, appendRemainingDays));
        reviewEligibility.put("appendRemainingTextI18n", I18nHelper.appendRemainingTextI18n(appendRemainingHours, appendRemainingDays));

        return Map.ofEntries(
                Map.entry("orderNo", orderNo),
                Map.entry("buyerUserId", buyerUserId),
                Map.entry("payStatus", order.getPayStatus()),
                Map.entry("fulfillStatus", order.getFulfillStatus()),
                Map.entry("timeline", events),
                Map.entry("reviewEligibility", reviewEligibility)
        );
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> getBuyerResaleOrderDetail(String orderNo, Long buyerUserId) {
        ResaleOrderEntity order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyerUser().getId().equals(buyerUserId)) {
            throw new BizException("仅允许查看本人订单", ErrorCode.ORDER_NOT_OWNER);
        }
        ResaleListingEntity listing = order.getListing();
        ProductEntity product = listing.getProduct();
        boolean completed = "COMPLETED".equals(order.getFulfillStatus());
        boolean hasReviewed = resaleReviewRepository
                .findByOrder_OrderNoAndUser_Id(orderNo, buyerUserId).isPresent();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderNo", order.getOrderNo());
        detail.put("buyerUserId", buyerUserId);
        detail.put("listingId", listing.getId());
        detail.put("productId", product.getId());
        detail.put("productBrand", product.getBrand());
        detail.put("productModel", product.getModel());
        detail.put("amount", order.getAmount());
        detail.put("payStatus", order.getPayStatus());
        detail.put("fulfillStatus", order.getFulfillStatus());
        detail.put("statusText", I18nHelper.orderStatusText(order.getPayStatus(), order.getFulfillStatus()));
        detail.put("statusTextI18n", I18nHelper.orderStatusTextI18n(order.getPayStatus(), order.getFulfillStatus()));
        detail.put("createdAt", order.getCreatedAt());
        detail.put("listingStatus", listing.getStatus());
        detail.put("listingSalePrice", listing.getSalePrice());
        detail.put("listingStock", listing.getStock());
        detail.put("canPay", "UNPAID".equals(order.getPayStatus()) && "WAIT_PAY".equals(order.getFulfillStatus()));
        detail.put("canCancel", "UNPAID".equals(order.getPayStatus()) && "WAIT_PAY".equals(order.getFulfillStatus()));
        detail.put("canConfirmReceipt", "PAID".equals(order.getPayStatus()) && "DELIVERED".equals(order.getFulfillStatus()));
        detail.put("canCreateReview", completed && !hasReviewed);
        return detail;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    @Cacheable(value = "resaleOrder", key = "#buyerUserId + '::list::' + #payStatus + '::' + #fulfillStatus + '::' + #sortBy + '::' + #sortOrder + '::' + #limit + '::' + #page + '::' + #size")
    public Map<String, Object> listBuyerResaleOrders(
            Long buyerUserId,
            @Nullable String payStatus,
            @Nullable String fulfillStatus,
            @Nullable String sortBy,
            @Nullable String sortOrder,
            @Nullable Integer limit,
            @Nullable Integer page,
            @Nullable Integer size
    ) {
        userAccountRepository.findById(buyerUserId)
                .orElseThrow(() -> new BizException("用户不存在: " + buyerUserId, ErrorCode.ORDER_NOT_FOUND));
        int normalizedLimit = normalizeOrderListLimit(limit);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        boolean pagingEnabled = page != null || size != null;
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortOrder = normalizeSortOrder(sortOrder);
        String normalizedPayStatus = normalizeFilterValue(payStatus);
        String normalizedFulfillStatus = normalizeFilterValue(fulfillStatus);
        Sort.Direction direction = "asc".equals(normalizedSortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(
                pagingEnabled ? normalizedPage : 0,
                pagingEnabled ? normalizedSize : normalizedLimit,
                Sort.by(direction, normalizedSortBy).and(Sort.by(Sort.Direction.DESC, "id")));
        Page<ResaleOrderEntity> orderPage = resaleOrderRepository.pageByBuyer(
                buyerUserId,
                normalizedPayStatus == null ? "" : normalizedPayStatus,
                normalizedFulfillStatus == null ? "" : normalizedFulfillStatus,
                pageable);
        List<Map<String, Object>> items = orderPage.getContent().stream()
                .map(this::toBuyerOrderListItem)
                .toList();
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("payStatus", normalizedPayStatus);
        filters.put("fulfillStatus", normalizedFulfillStatus);
        filters.put("sortBy", normalizedSortBy);
        filters.put("sortOrder", normalizedSortOrder);
        filters.put("limit", normalizedLimit);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("buyerUserId", buyerUserId);
        response.put("filters", filters);
        response.put("total", orderPage.getTotalElements());
        response.put("items", items);
        if (pagingEnabled) {
            response.put("totalMatched", orderPage.getTotalElements());
            response.put("page", normalizedPage);
            response.put("size", normalizedSize);
            response.put("hasMore", orderPage.hasNext());
        }
        return response;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> getResaleOrderStatusDictionary() {
        List<Map<String, Object>> payStatus = List.of(
                statusDictItem("UNPAID", "未支付", "Unpaid"),
                statusDictItem("PAID", "已支付", "Paid"),
                statusDictItem("REFUNDED", "已退款", "Refunded")
        );
        List<Map<String, Object>> fulfillStatus = List.of(
                statusDictItem("WAIT_PAY", "待支付", "Pending Payment"),
                statusDictItem("TO_DELIVER", "待发货", "Pending Shipment"),
                statusDictItem("DELIVERED", "待收货", "Awaiting Receipt"),
                statusDictItem("COMPLETED", "已完成", "Completed"),
                statusDictItem("CANCELLED", "已取消", "Cancelled"),
                statusDictItem("AUTO_CLOSED", "已关闭", "Auto Closed"),
                statusDictItem("REFUNDED", "已退款", "Refunded")
        );
        return Map.of("schemaVersion", "1.0.0", "payStatus", payStatus, "fulfillStatus", fulfillStatus, "generatedAt", LocalDateTime.now());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> summarizeBuyerResaleOrders(Long buyerUserId, @Nullable Integer lookbackDays) {
        userAccountRepository.findById(buyerUserId)
                .orElseThrow(() -> new BizException("用户不存在: " + buyerUserId, ErrorCode.ORDER_NOT_FOUND));
        int normalizedLookbackDays = normalizeSummaryLookbackDays(lookbackDays);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fromTime = normalizedLookbackDays <= 0 ? null : now.minusDays(normalizedLookbackDays);
        List<ResaleOrderEntity> allOrders = resaleOrderRepository.findByBuyerUser_IdOrderByCreatedAtDesc(buyerUserId);
        List<ResaleOrderEntity> orders = allOrders.stream()
                .filter(order -> fromTime == null || order.getCreatedAt() == null || !order.getCreatedAt().isBefore(fromTime))
                .toList();

        Map<String, Long> payStatusCounts = orders.stream().collect(Collectors.groupingBy(ResaleOrderEntity::getPayStatus, Collectors.counting()));
        Map<String, Long> fulfillStatusCounts = orders.stream().collect(Collectors.groupingBy(ResaleOrderEntity::getFulfillStatus, Collectors.counting()));

        BigDecimal totalAmount = orders.stream().map(ResaleOrderEntity::getAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = orders.stream().filter(o -> "PAID".equals(o.getPayStatus())).map(ResaleOrderEntity::getAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedAmount = orders.stream().filter(o -> "REFUNDED".equals(o.getPayStatus()) || "REFUNDED".equals(o.getFulfillStatus())).map(ResaleOrderEntity::getAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        long refundedCount = orders.stream().filter(o -> "REFUNDED".equals(o.getPayStatus()) || "REFUNDED".equals(o.getFulfillStatus())).count();
        long completedCount = orders.stream().filter(o -> "COMPLETED".equals(o.getFulfillStatus())).count();

        BigDecimal completionRate = orders.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(completedCount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
        BigDecimal refundRate = orders.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(refundedCount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
        BigDecimal healthScore = calculateOrderHealthScore(completionRate, refundRate);
        String healthLevel = resolveOrderHealthLevel(completionRate, refundedCount, orders.size());

        Map<String, Object> summaryScope = new LinkedHashMap<>();
        summaryScope.put("lookbackDays", normalizedLookbackDays);
        summaryScope.put("fromTime", fromTime);
        summaryScope.put("toTime", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("buyerUserId", buyerUserId);
        response.put("summaryScope", summaryScope);
        response.put("totalOrders", orders.size());
        response.put("payStatusCounts", payStatusCounts);
        response.put("fulfillStatusCounts", fulfillStatusCounts);
        response.put("refundedOrders", refundedCount);
        response.put("totalAmount", totalAmount);
        response.put("paidAmount", paidAmount);
        response.put("refundedAmount", refundedAmount);
        response.put("completedOrders", completedCount);
        response.put("completionRate", completionRate);
        response.put("refundRate", refundRate);
        response.put("healthScore", healthScore);
        response.put("healthLevel", healthLevel);
        response.put("healthLevelI18n", I18nHelper.healthLevelI18n(healthLevel));
        response.put("generatedAt", now);
        return response;
    }

    @ResaleTransactional
    public int autoCloseExpiredUnpaidOrders(int expireMinutes, int batchSize) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(expireMinutes);
        int closedCount = 0;
        while (true) {
            List<ResaleOrderEntity> expiredOrders = resaleOrderRepository.findByPayStatusAndFulfillStatusAndCreatedAtBefore(
                    "UNPAID", "WAIT_PAY", threshold, PageRequest.of(0, batchSize));
            if (expiredOrders.isEmpty()) break;
            int roundClosed = 0;
            for (ResaleOrderEntity order : expiredOrders) {
                int updated = resaleOrderRepository.compareAndSetStatus(
                        order.getOrderNo(), "UNPAID", "WAIT_PAY", "UNPAID", "AUTO_CLOSED");
                if (updated == 0) {
                    // 用户取消或支付已先完成，不能再关单或释放库存
                    continue;
                }
                releaseListingStock(order.getListing().getId());
                couponService.releaseForOrder(order.getId());
                returnOrderPoints(order);
                auditLogService.logAction("RESALE_ORDER_AUTO_CLOSE", "RESALE_ORDER", order.getOrderNo(), "expireMinutes=" + expireMinutes);
                notificationService.push(order.getBuyerUser().getId(), "ORDER_AUTO_CLOSED", "订单超时未支付已关闭",
                        "订单 " + order.getOrderNo() + " 超时未支付已自动关闭，库存与优惠券已退回。", order.getId(), "RESALE_ORDER");
                eventOutbox.publish(new com.suno.mall.marketplace.api.event.MarketplaceStockReleased(
                        order.getOrderNo(), order.getListing().getId(), "AUTO_CLOSE"));
                closedCount++;
                roundClosed++;
            }
            if (roundClosed == 0) break;
        }
        return closedCount;
    }

    @ResaleTransactional
    public int autoConfirmDeliveredOrders(int confirmAfterMinutes, int batchSize, AuditContext auditContext) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(confirmAfterMinutes);
        int confirmedCount = 0;
        while (true) {
            List<ResaleOrderEntity> deliveredOrders = resaleOrderRepository.findByPayStatusAndFulfillStatus(
                    "PAID", "DELIVERED", PageRequest.of(0, batchSize));
            if (deliveredOrders.isEmpty()) break;
            int roundConfirmed = 0;
            for (ResaleOrderEntity order : deliveredOrders) {
                if (!auditLogService.isDeliveredBefore(order.getOrderNo(), threshold)) continue;
                int updated = resaleOrderRepository.compareAndSetStatus(
                        order.getOrderNo(), "PAID", "DELIVERED", "PAID", "COMPLETED");
                if (updated == 0) continue;
                auditLogService.logAction("RESALE_ORDER_AUTO_RECEIVE", "RESALE_ORDER", order.getOrderNo(),
                        "confirmAfterMinutes=" + confirmAfterMinutes + auditLogService.buildAuditContextSuffix(auditContext));
                eventOutbox.publish(new com.suno.mall.marketplace.api.event.MarketplaceFulfillmentCompleted(
                        order.getOrderNo(), order.getBuyerUser().getId()));
                confirmedCount++;
                roundConfirmed++;
            }
            if (roundConfirmed == 0) break;
        }
        return confirmedCount;
    }

    // ---- private helpers ----

    private BigDecimal calculateOrderHealthScore(BigDecimal completionRate, BigDecimal refundRate) {
        BigDecimal score = completionRate.multiply(new BigDecimal("0.7"))
                .add(BigDecimal.valueOf(100).subtract(refundRate).multiply(new BigDecimal("0.3")));
        if (score.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (score.compareTo(BigDecimal.valueOf(100)) > 0) return BigDecimal.valueOf(100);
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveOrderHealthLevel(BigDecimal completionRate, long refundedCount, int totalOrders) {
        if (totalOrders <= 0) return "NEUTRAL";
        BigDecimal refundRatio = BigDecimal.valueOf(refundedCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        if (completionRate.compareTo(new BigDecimal("70")) >= 0 && refundRatio.compareTo(new BigDecimal("20")) <= 0) return "GOOD";
        if (completionRate.compareTo(new BigDecimal("40")) >= 0 && refundRatio.compareTo(new BigDecimal("35")) <= 0) return "NORMAL";
        return "ATTENTION";
    }

    private Map<String, Object> toBuyerOrderListItem(ResaleOrderEntity order) {
        ProductEntity product = order.getListing().getProduct();
        return Map.ofEntries(
                Map.entry("orderNo", order.getOrderNo()),
                Map.entry("listingId", order.getListing().getId()),
                Map.entry("productId", product.getId()),
                Map.entry("productBrand", product.getBrand()),
                Map.entry("productModel", product.getModel()),
                Map.entry("amount", order.getAmount()),
                Map.entry("payStatus", order.getPayStatus()),
                Map.entry("fulfillStatus", order.getFulfillStatus()),
                Map.entry("statusText", I18nHelper.orderStatusText(order.getPayStatus(), order.getFulfillStatus())),
                Map.entry("statusTextI18n", I18nHelper.orderStatusTextI18n(order.getPayStatus(), order.getFulfillStatus())),
                Map.entry("createdAt", order.getCreatedAt())
        );
    }

    private Map<String, Object> statusDictItem(String code, String zhCn, String enUs) {
        return Map.of("code", code, "label", zhCn, "labelI18n", Map.of("zh-CN", zhCn, "en-US", enUs));
    }

    private int normalizeOrderListLimit(Integer limit) { return limit == null ? 20 : Math.max(1, Math.min(limit, 100)); }
    private int normalizePage(Integer page) { return page == null ? 0 : Math.max(0, page); }
    private int normalizePageSize(Integer size) { return size == null ? 20 : Math.max(1, Math.min(size, 100)); }
    private int normalizeSummaryLookbackDays(Integer lookbackDays) { return lookbackDays == null ? 30 : Math.max(1, Math.min(lookbackDays, 365)); }
    private String normalizeFilterValue(@Nullable String value) { return (value == null || value.isBlank()) ? null : value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeSortBy(@Nullable String sortBy) {
        if (sortBy == null || sortBy.isBlank()) return "createdAt";
        String n = sortBy.trim();
        if ("createdAt".equalsIgnoreCase(n)) return "createdAt";
        if ("amount".equalsIgnoreCase(n)) return "amount";
        throw new IllegalArgumentException("排序字段仅支持 createdAt 或 amount");
    }
    private String normalizeSortOrder(@Nullable String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank()) return "desc";
        String n = sortOrder.trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(n) || "desc".equals(n)) return n;
        throw new IllegalArgumentException("排序方向仅支持 asc 或 desc");
    }
    private void releaseListingStock(Long listingId) {
        int updated = resaleListingRepository.releaseOneStock(
                listingId, LISTING_STATUS_ON_SHELF, LISTING_STATUS_SOLD_OUT, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException("商品不存在，库存释放失败: " + listingId, ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
