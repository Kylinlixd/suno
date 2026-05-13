
package com.suno.mall.service;

import org.jspecify.annotations.Nullable;
import com.recycle.mall.entity.ProductDraft;
import com.recycle.mall.entity.ValuationResult;
import com.recycle.mall.entity.LogisticsTrackEntity;
import com.recycle.mall.entity.PointsLedgerEntity;
import com.recycle.mall.entity.ProductEntity;
import com.recycle.mall.entity.RecycleOrderEntity;
import com.recycle.mall.entity.UserAccountEntity;
import com.recycle.mall.dao.LogisticsTrackRepository;
import com.recycle.mall.dao.PointsLedgerRepository;
import com.recycle.mall.dao.ProductRepository;
import com.recycle.mall.dao.RecycleOrderRepository;
import com.recycle.mall.dao.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 回收订单服务
 */
@Service
public class RecycleOrderService {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_QUALITY_CHECKED = "QUALITY_CHECKED";
    private static final String STATUS_PRICE_REVIEWED = "PRICE_REVIEWED";
    private static final String STATUS_LISTED = "LISTED";

    private final AiAuditService aiAuditService;
    private final SnParseService snParseService;
    private final ValuationService valuationService;
    private final LogisticsService logisticsService;
    private final PointsService pointsService;
    private final UserAccountRepository userAccountRepository;
    private final ProductRepository productRepository;
    private final RecycleOrderRepository recycleOrderRepository;
    private final LogisticsTrackRepository logisticsTrackRepository;
    private final PointsLedgerRepository pointsLedgerRepository;
    private final AuditLogService auditLogService;

    public RecycleOrderService(
            AiAuditService aiAuditService,
            SnParseService snParseService,
            ValuationService valuationService,
            LogisticsService logisticsService,
            PointsService pointsService,
            UserAccountRepository userAccountRepository,
            ProductRepository productRepository,
            RecycleOrderRepository recycleOrderRepository,
            LogisticsTrackRepository logisticsTrackRepository,
            PointsLedgerRepository pointsLedgerRepository,
            AuditLogService auditLogService
    ) {
        this.aiAuditService = aiAuditService;
        this.snParseService = snParseService;
        this.valuationService = valuationService;
        this.logisticsService = logisticsService;
        this.pointsService = pointsService;
        this.userAccountRepository = userAccountRepository;
        this.productRepository = productRepository;
        this.recycleOrderRepository = recycleOrderRepository;
        this.logisticsTrackRepository = logisticsTrackRepository;
        this.pointsLedgerRepository = pointsLedgerRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Map<String, Object> createRecycleOrder(Long userId, String snCode, String imageUrl, int wearScore, int recycleCount) {
        if (!aiAuditService.passImageAudit(imageUrl)) {
            throw new IllegalArgumentException("图片审核不通过，请重新上传商品图片");
        }
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        ProductDraft draft = snParseService.parse(snCode, imageUrl, wearScore);
        ValuationResult valuation = valuationService.evaluate(draft);

        String orderNo = "RCY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String trackingNo = logisticsService.createTrackingNo(orderNo);
        int points = pointsService.calculateRecyclePoints(user.getLevel(), recycleCount);

        ProductEntity product = new ProductEntity();
        product.setSnCode(draft.snCode());
        product.setBrand(draft.brand());
        product.setModel(draft.model());
        product.setProductionDate(draft.productionDate());
        product.setImageUrl(draft.imageUrl());
        product.setWearScore(draft.wearScore());
        product.setRecycleGrade(valuation.gradeLevel().name());
        product.setEstimatedRecyclePrice(valuation.estimatedPrice());
        productRepository.save(product);

        RecycleOrderEntity order = new RecycleOrderEntity();
        order.setOrderNo(orderNo);
        order.setUser(user);
        order.setProduct(product);
        order.setEstimatedPrice(valuation.estimatedPrice());
        order.setGrade(valuation.gradeLevel().name());
        order.setStatus(STATUS_CREATED);
        order.setCreatedAt(LocalDateTime.now());
        recycleOrderRepository.save(order);
        auditLogService.logAction("RECYCLE_ORDER_CREATE", "RECYCLE_ORDER", orderNo, "estimatedPrice=" + valuation.estimatedPrice());

        LogisticsTrackEntity logistics = new LogisticsTrackEntity();
        logistics.setTrackingNo(trackingNo);
        logistics.setOrder(order);
        logistics.setStatus("TO_SHIP");
        logistics.setUpdatedAt(LocalDateTime.now());
        logisticsTrackRepository.save(logistics);

        PointsLedgerEntity ledger = new PointsLedgerEntity();
        ledger.setUser(user);
        ledger.setPointsDelta(points);
        ledger.setReason("RECYCLE_ORDER:" + orderNo);
        ledger.setCreatedAt(LocalDateTime.now());
        pointsLedgerRepository.save(ledger);

        user.setPoints(user.getPoints() + points);
        userAccountRepository.save(user);

        return Map.ofEntries(
                Map.entry("orderNo", orderNo),
                Map.entry("trackingNo", trackingNo),
                Map.entry("estimatedGrade", valuation.gradeLevel()),
                Map.entry("estimatedPrice", valuation.estimatedPrice()),
                Map.entry("rewardPoints", points),
                Map.entry("userPointsTotal", user.getPoints()),
                Map.entry("product", draft)
        );
    }

    public String queryLogisticsStatus(String trackingNo) {
        LogisticsTrackEntity track = logisticsTrackRepository.findByTrackingNo(trackingNo)
                .orElseThrow(() -> new IllegalArgumentException("物流单不存在: " + trackingNo));
        String latestStatus = logisticsService.queryStatus(trackingNo);
        track.setStatus(latestStatus);
        track.setUpdatedAt(LocalDateTime.now());
        logisticsTrackRepository.save(track);
        return latestStatus;
    }

    public List<Map<String, Object>> listRecycleOrders() {
        return recycleOrderRepository.findAll().stream().map(order -> Map.<String, Object>of(
                "orderNo", order.getOrderNo(),
                "status", order.getStatus(),
                "grade", order.getGrade(),
                "estimatedPrice", order.getEstimatedPrice(),
                "createdAt", order.getCreatedAt()
        )).toList();
    }

    @Transactional
    public Map<String, Object> transitionOrder(String orderNo, String action, @Nullable String reviewedGrade) {
        RecycleOrderEntity order = recycleOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("回收单不存在: " + orderNo));
        String nextStatus = nextStatus(order.getStatus(), action);
        if (reviewedGrade != null && !reviewedGrade.isBlank()) {
            order.setGrade(reviewedGrade);
        }
        order.setStatus(nextStatus);
        recycleOrderRepository.save(order);
        auditLogService.logAction("RECYCLE_ORDER_TRANSITION", "RECYCLE_ORDER", orderNo, "action=" + action + ",status=" + nextStatus);
        return Map.of(
                "orderNo", order.getOrderNo(),
                "grade", order.getGrade(),
                "status", order.getStatus(),
                "action", action
        );
    }

    private String nextStatus(String currentStatus, String action) {
        return switch (action) {
            case "QUALITY_CHECK" -> {
                assertStatus(currentStatus, STATUS_CREATED, action);
                yield STATUS_QUALITY_CHECKED;
            }
            case "PRICE_REVIEW" -> {
                assertStatus(currentStatus, STATUS_QUALITY_CHECKED, action);
                yield STATUS_PRICE_REVIEWED;
            }
            case "LIST_ON_SHELF" -> {
                assertStatus(currentStatus, STATUS_PRICE_REVIEWED, action);
                yield STATUS_LISTED;
            }
            default -> throw new IllegalArgumentException("不支持的审核动作: " + action);
        };
    }

    private void assertStatus(String currentStatus, String expectedStatus, String action) {
        if (!expectedStatus.equals(currentStatus)) {
            throw new IllegalArgumentException(
                    "当前状态不允许执行该动作, status=" + currentStatus + ", action=" + action
            );
        }
    }
}
