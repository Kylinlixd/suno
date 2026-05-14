
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
import com.suno.mall.dao.ResaleListingRepository;
import com.suno.mall.dao.ResaleOrderRepository;
import com.suno.mall.dao.ResaleReviewRepository;
import com.suno.mall.dao.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
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
    private final AuditLogService auditLogService;
    private final ResaleListingService resaleListingService;

    public ResaleOrderService(
            UserAccountRepository userAccountRepository,
            ResaleOrderRepository resaleOrderRepository,
            ResaleReviewRepository resaleReviewRepository,
            ResaleListingRepository resaleListingRepository,
            PaymentIdempotencyRepository paymentIdempotencyRepository,
            AuditLogService auditLogService,
            ResaleListingService resaleListingService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.resaleOrderRepository = resaleOrderRepository;
        this.resaleReviewRepository = resaleReviewRepository;
        this.resaleListingRepository = resaleListingRepository;
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
        this.auditLogService = auditLogService;
        this.resaleListingService = resaleListingService;
    }

    @Transactional
    public Map<String, Object> createResaleOrder(Long buyerUserId, Long listingId) {
        UserAccountEntity buyer = userAccountRepository.findById(buyerUserId)
                .orElseThrow(() -> new BizException("买家不存在: " + buyerUserId, ErrorCode.ORDER_NOT_FOUND));
        ResaleListingEntity listing = resaleListingRepository.findById(listingId)
                .orElseThrow(() -> new BizException("商品不存在: " + listingId, ErrorCode.ORDER_NOT_FOUND));
        if (!LISTING_STATUS_ON_SHELF.equals(listing.getStatus())) {
            throw new BizException("商品已下架或售罄", ErrorCode.ORDER_LISTING_UNAVAILABLE);
        }
        if (listing.getStock() <= 0) {
            throw new BizException("库存不足", ErrorCode.ORDER_STOCK_INSUFFICIENT);
        }
        try {
            listing.setStock(listing.getStock() - 1);
            if (listing.getStock() == 0) {
                listing.setStatus(LISTING_STATUS_SOLD_OUT);
            }
            resaleListingRepository.saveAndFlush(listing);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new BizException("下单冲突，请刷新后重试", ErrorCode.ORDER_CONCURRENT_CONFLICT);
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
        resaleOrderRepository.save(order);
        auditLogService.logAction("RESALE_ORDER_CREATE", "RESALE_ORDER", orderNo, "listingId=" + listingId + ",buyerUserId=" + buyerUserId);

        return Map.of(
                "orderNo", order.getOrderNo(),
                "listingId", listing.getId(),
                "amount", order.getAmount(),
                "payStatus", order.getPayStatus(),
                "fulfillStatus", order.getFulfillStatus()
        );
    }


    @Transactional
    public Map<String, Object> markResaleOrderPaid(String orderNo) {
        ResaleOrderEntity order = resaleOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!"UNPAID".equals(order.getPayStatus())) {
            throw new BizException("订单支付状态异常: " + order.getPayStatus(), ErrorCode.ORDER_STATUS_CONFLICT);
        }
        order.setPayStatus("PAID");
        order.setFulfillStatus("TO_DELIVER");
        resaleOrderRepository.save(order);
        auditLogService.logAction("RESALE_ORDER_PAY", "RESALE_ORDER", orderNo, "payStatus=PAID");
        return Map.of("orderNo", order.getOrderNo(), "payStatus", order.getPayStatus(), "fulfillStatus", order.getFulfillStatus());
    }

    @Transactional
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
        return Map.of("orderNo", paidResult.get("orderNo"), "payStatus", paidResult.get("payStatus"),
                "fulfillStatus", paidResult.get("fulfillStatus"), "idempotentReplay", false);
    }

    @Transactional
    public Map<String, Object> deliverResaleOrder(String orderNo, AuditContext auditContext) {
        ResaleOrderEntity order = resaleOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!"PAID".equals(order.getPayStatus())) {
            throw new BizException("未支付订单不可发货", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        order.setFulfillStatus("DELIVERED");
        resaleOrderRepository.save(order);
        auditLogService.logAction("RESALE_ORDER_DELIVER", "RESALE_ORDER", orderNo,
                "fulfillStatus=DELIVERED" + auditLogService.buildAuditContextSuffix(auditContext));
        return Map.of("orderNo", order.getOrderNo(), "payStatus", order.getPayStatus(), "fulfillStatus", order.getFulfillStatus());
    }

    @Transactional
    public Map<String, Object> confirmResaleOrderReceipt(String orderNo, Long buyerUserId) {
        ResaleOrderEntity order = resaleOrderRepository.findByOrderNo(orderNo)
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
        order.setFulfillStatus("COMPLETED");
        resaleOrderRepository.save(order);
        auditLogService.logAction("RESALE_ORDER_RECEIVE", "RESALE_ORDER", orderNo, "fulfillStatus=COMPLETED,buyerUserId=" + buyerUserId);
        return Map.of("orderNo", order.getOrderNo(), "buyerUserId", buyerUserId, "payStatus", order.getPayStatus(), "fulfillStatus", order.getFulfillStatus());
    }

    @Transactional
    public Map<String, Object> cancelUnpaidResaleOrder(String orderNo) {
        ResaleOrderEntity order = resaleOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!"UNPAID".equals(order.getPayStatus())) {
            throw new BizException("仅 UNPAID 订单可取消", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if (!"WAIT_PAY".equals(order.getFulfillStatus())) {
            throw new BizException("当前履约状态不可取消: " + order.getFulfillStatus(), ErrorCode.ORDER_STATUS_CONFLICT);
        }
        order.setFulfillStatus("CANCELLED");
        resaleOrderRepository.save(order);
        restoreListingStock(order.getListing());
        auditLogService.logAction("RESALE_ORDER_CANCEL", "RESALE_ORDER", orderNo, "fulfillStatus=CANCELLED");
        return Map.of("orderNo", order.getOrderNo(), "payStatus", order.getPayStatus(), "fulfillStatus", order.getFulfillStatus());
    }

    @Transactional
    public Map<String, Object> refundPaidResaleOrder(String orderNo, AuditContext auditContext) {
        ResaleOrderEntity order = resaleOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("二销订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND));
        if (!"PAID".equals(order.getPayStatus())) {
            throw new BizException("仅 PAID 订单可退款", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        if ("REFUNDED".equals(order.getFulfillStatus())) {
            throw new BizException("订单已退款", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        order.setPayStatus("REFUNDED");
        order.setFulfillStatus("REFUNDED");
        resaleOrderRepository.save(order);
        restoreListingStock(order.getListing());
        auditLogService.logAction("RESALE_ORDER_REFUND", "RESALE_ORDER", orderNo,
                "payStatus=REFUNDED" + auditLogService.buildAuditContextSuffix(auditContext));
        return Map.of("orderNo", order.getOrderNo(), "payStatus", order.getPayStatus(), "fulfillStatus", order.getFulfillStatus());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> queryResaleOrderTrack(String orderNo, Long buyerUserId) {
        ResaleOrderEntity order = resaleOrderRepository.findByOrderNo(orderNo)
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

    @Transactional(readOnly = true)
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
        Comparator<ResaleOrderEntity> orderComparator = buildOrderComparator(normalizedSortBy, normalizedSortOrder);
        List<Map<String, Object>> filtered = resaleOrderRepository.findByBuyerUser_IdOrderByCreatedAtDesc(buyerUserId).stream()
                .filter(order -> normalizedPayStatus == null || normalizedPayStatus.equals(order.getPayStatus()))
                .filter(order -> normalizedFulfillStatus == null || normalizedFulfillStatus.equals(order.getFulfillStatus()))
                .sorted(orderComparator)
                .map(this::toBuyerOrderListItem)
                .toList();
        List<Map<String, Object>> items;
        int totalMatched = filtered.size();
        boolean hasMore = false;
        if (pagingEnabled) {
            int start = Math.min(totalMatched, normalizedPage * normalizedSize);
            int end = Math.min(totalMatched, start + normalizedSize);
            items = filtered.subList(start, end);
            hasMore = end < totalMatched;
        } else {
            items = filtered.stream().limit(normalizedLimit).toList();
        }
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("payStatus", normalizedPayStatus);
        filters.put("fulfillStatus", normalizedFulfillStatus);
        filters.put("sortBy", normalizedSortBy);
        filters.put("sortOrder", normalizedSortOrder);
        filters.put("limit", normalizedLimit);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("buyerUserId", buyerUserId);
        response.put("filters", filters);
        response.put("total", items.size());
        response.put("items", items);
        if (pagingEnabled) {
            response.put("totalMatched", totalMatched);
            response.put("page", normalizedPage);
            response.put("size", normalizedSize);
            response.put("hasMore", hasMore);
        }
        return response;
    }

    @Transactional(readOnly = true)
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
                statusDictItem("REFUNDED", "已退款", "Refunded")
        );
        return Map.of("schemaVersion", "1.0.0", "payStatus", payStatus, "fulfillStatus", fulfillStatus, "generatedAt", LocalDateTime.now());
    }

    @Transactional(readOnly = true)
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

    @Transactional
    public int autoCloseExpiredUnpaidOrders(int expireMinutes, int batchSize) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(expireMinutes);
        int closedCount = 0;
        while (true) {
            List<ResaleOrderEntity> expiredOrders = resaleOrderRepository.findByPayStatusAndFulfillStatusAndCreatedAtBefore(
                    "UNPAID", "WAIT_PAY", threshold, PageRequest.of(0, batchSize));
            if (expiredOrders.isEmpty()) break;
            for (ResaleOrderEntity order : expiredOrders) {
                order.setFulfillStatus("AUTO_CLOSED");
                resaleOrderRepository.save(order);
                resaleListingService.restoreListingStock(order.getListing());
                auditLogService.logAction("RESALE_ORDER_AUTO_CLOSE", "RESALE_ORDER", order.getOrderNo(), "expireMinutes=" + expireMinutes);
                closedCount++;
            }
        }
        return closedCount;
    }

    @Transactional
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
                order.setFulfillStatus("COMPLETED");
                resaleOrderRepository.save(order);
                auditLogService.logAction("RESALE_ORDER_AUTO_RECEIVE", "RESALE_ORDER", order.getOrderNo(),
                        "confirmAfterMinutes=" + confirmAfterMinutes + auditLogService.buildAuditContextSuffix(auditContext));
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
    private Comparator<ResaleOrderEntity> buildOrderComparator(String sortBy, String sortOrder) {
        Comparator<ResaleOrderEntity> comparator = switch (sortBy) {
            case "amount" -> Comparator.comparing(ResaleOrderEntity::getAmount, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(ResaleOrderEntity::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
        };
        return "asc".equals(sortOrder) ? comparator : comparator.reversed();
    }

    private void restoreListingStock(ResaleListingEntity listing) {
        listing.setStock(listing.getStock() + 1);
        listing.setStatus(LISTING_STATUS_ON_SHELF);
        resaleListingRepository.save(listing);
    }
}
