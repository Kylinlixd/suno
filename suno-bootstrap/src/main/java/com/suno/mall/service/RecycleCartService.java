package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.dao.LogisticsTrackRepository;
import com.suno.mall.dao.PointsLedgerRepository;
import com.suno.mall.dao.ProductRepository;
import com.suno.mall.dao.RecycleCartItemRepository;
import com.suno.mall.dao.RecycleOrderRepository;
import com.suno.mall.dao.UserAccountRepository;
import com.suno.mall.entity.LogisticsTrackEntity;
import com.suno.mall.entity.PointsLedgerEntity;
import com.suno.mall.entity.ProductEntity;
import com.suno.mall.entity.RecycleCartItem;
import com.suno.mall.entity.RecycleOrderEntity;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.core.event.EventOutbox;
import com.suno.mall.recycle.api.event.RecycleOrderSubmitted;
import com.suno.mall.recycle.api.event.RecyclePointsPosted;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 回收购物车服务：用户暂存待回收旧物（扫码获取书籍信息或手动填写），
 * 可连续添加多种品类，一次性提交为多笔回收单。与购买购物车同构，区别是"卖"。
 */
@Service
public class RecycleCartService {

    private final RecycleCartItemRepository recycleCartItemRepository;
    private final BookScanService bookScanService;
    private final UserAccountRepository userAccountRepository;
    private final ProductRepository productRepository;
    private final RecycleOrderRepository recycleOrderRepository;
    private final LogisticsTrackRepository logisticsTrackRepository;
    private final PointsLedgerRepository pointsLedgerRepository;
    private final PointsService pointsService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EventOutbox eventOutbox;
    private final LogisticsService logisticsService;
    private final MemberLevelService memberLevelService;

    public RecycleCartService(
            RecycleCartItemRepository recycleCartItemRepository,
            BookScanService bookScanService,
            UserAccountRepository userAccountRepository,
            ProductRepository productRepository,
            RecycleOrderRepository recycleOrderRepository,
            LogisticsTrackRepository logisticsTrackRepository,
            PointsLedgerRepository pointsLedgerRepository,
            PointsService pointsService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            EventOutbox eventOutbox,
            LogisticsService logisticsService,
            MemberLevelService memberLevelService) {
        this.recycleCartItemRepository = recycleCartItemRepository;
        this.bookScanService = bookScanService;
        this.userAccountRepository = userAccountRepository;
        this.productRepository = productRepository;
        this.recycleOrderRepository = recycleOrderRepository;
        this.logisticsTrackRepository = logisticsTrackRepository;
        this.pointsLedgerRepository = pointsLedgerRepository;
        this.pointsService = pointsService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.eventOutbox = eventOutbox;
        this.logisticsService = logisticsService;
        this.memberLevelService = memberLevelService;
    }

    /** 扫码添加：ISBN → Open Library → 估价 → 加入回收车 */
    @Transactional
    public Map<String, Object> scanAndAdd(Long userId, String isbn, int wearScore) {
        Map<String, Object> bookInfo = bookScanService.scanByIsbn(isbn);
        int safeWear = Math.min(100, Math.max(0, wearScore));
        Map<String, Object> valuation = bookScanService.estimatePrice(
                (Integer) bookInfo.get("publishYear"), safeWear);

        RecycleCartItem item = new RecycleCartItem();
        item.setUser(userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在", ErrorCode.PARAM_INVALID)));
        item.setBarcode((String) bookInfo.get("barcode"));
        item.setTitle((String) bookInfo.get("title"));
        item.setAuthor((String) bookInfo.getOrDefault("author", ""));
        item.setPublisher((String) bookInfo.getOrDefault("publisher", ""));
        item.setPublishYear((Integer) bookInfo.get("publishYear"));
        item.setCategory("图书");
        item.setWearScore(safeWear);
        item.setEstimatedPrice((BigDecimal) valuation.get("estimatedPrice"));
        item.setGrade((String) valuation.get("grade"));
        item.setSource("SCAN");
        item.setStatus("IN_CART");
        item.setCreatedAt(LocalDateTime.now());
        recycleCartItemRepository.save(item);

        return toRow(item);
    }

    /** 手动添加：非书籍类旧物（衣物/家居/美妆等） */
    @Transactional
    public Map<String, Object> addManualItem(Long userId, String title, String category,
                                             int wearScore, BigDecimal estimatedPrice) {
        if (title == null || title.isBlank()) {
            throw new BizException("请填写物品名称", ErrorCode.PARAM_INVALID);
        }
        int safeWear = Math.min(100, Math.max(0, wearScore));
        String grade = safeWear >= 80 ? "GOOD" : (safeWear >= 60 ? "MEDIUM" : "UNQUALIFIED");

        RecycleCartItem item = new RecycleCartItem();
        item.setUser(userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在", ErrorCode.PARAM_INVALID)));
        item.setBarcode(null);
        item.setTitle(title.trim());
        item.setCategory(category != null && !category.isBlank() ? category.trim() : "其他");
        item.setWearScore(safeWear);
        item.setEstimatedPrice(estimatedPrice != null ? estimatedPrice : BigDecimal.ZERO);
        item.setGrade(grade);
        item.setSource("MANUAL");
        item.setStatus("IN_CART");
        item.setCreatedAt(LocalDateTime.now());
        recycleCartItemRepository.save(item);

        return toRow(item);
    }

    /** 查看回收车 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCart(Long userId) {
        return recycleCartItemRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(userId, "IN_CART")
                .stream().map(RecycleCartService::toRow).toList();
    }

    /** 从回收车移除 */
    @Transactional
    public void removeItem(Long userId, Long itemId) {
        RecycleCartItem item = recycleCartItemRepository.findByIdAndUser_Id(itemId, userId)
                .filter(cart -> "IN_CART".equals(cart.getStatus()))
                .orElseThrow(() -> new BizException("回收车条目不存在或状态不允许", ErrorCode.PARAM_INVALID));
        recycleCartItemRepository.delete(item);
    }

    /** 提交回收车：每件旧物 → 商品 + 回收单 + 积分 + 通知 + 事件 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitCart(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在", ErrorCode.PARAM_INVALID));

        List<RecycleCartItem> cartItems = recycleCartItemRepository
                .findByUser_IdAndStatusOrderByCreatedAtDesc(userId, "IN_CART");
        if (cartItems.isEmpty()) {
            throw new BizException("回收车为空，请先添加旧物", ErrorCode.PARAM_INVALID);
        }

        List<String> orderNos = new java.util.ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalPoints = 0;

        for (RecycleCartItem cartItem : cartItems) {
            // 创建商品
            String snCode = cartItem.getBarcode();
            if (snCode == null || snCode.isBlank() || productRepository.findBySnCode(snCode).isPresent()) {
                snCode = "AUTO-" + UUID.randomUUID().toString().substring(0, 8);
            }
            ProductEntity product = new ProductEntity();
            product.setSnCode(snCode);
            product.setBrand(cartItem.getCategory() != null ? cartItem.getCategory() : "Suno");
            product.setModel(cartItem.getTitle());
            product.setProductionDate(LocalDate.now().minusYears(2));
            product.setImageUrl("https://cdn.suno.dev/recycle-" + UUID.randomUUID().toString().substring(0, 8) + ".jpg");
            product.setWearScore(cartItem.getWearScore());
            product.setRecycleGrade(cartItem.getGrade());
            product.setEstimatedRecyclePrice(cartItem.getEstimatedPrice());
            productRepository.save(product);

            // 创建回收单
            String orderNo = "RCY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String trackingNo = logisticsService.createTrackingNo(orderNo);

            RecycleOrderEntity order = new RecycleOrderEntity();
            order.setOrderNo(orderNo);
            order.setUser(user);
            order.setProduct(product);
            order.setEstimatedPrice(cartItem.getEstimatedPrice());
            order.setGrade(cartItem.getGrade());
            order.setStatus("CREATED");
            order.setCreatedAt(LocalDateTime.now());
            recycleOrderRepository.save(order);

            // 物流追踪
            LogisticsTrackEntity track = new LogisticsTrackEntity();
            track.setTrackingNo(trackingNo);
            track.setOrder(order);
            track.setStatus("TO_SHIP");
            track.setUpdatedAt(LocalDateTime.now());
            logisticsTrackRepository.save(track);

            // 积分
            int points = pointsService.calculateRecyclePoints(user.getLevel(), 1);
            PointsLedgerEntity ledger = new PointsLedgerEntity();
            ledger.setUser(user);
            ledger.setPointsDelta(points);
            ledger.setReason("RECYCLE_ORDER:" + orderNo);
            ledger.setCreatedAt(LocalDateTime.now());
            pointsLedgerRepository.save(ledger);

            totalPoints += points;
            totalPrice = totalPrice.add(cartItem.getEstimatedPrice());
            orderNos.add(orderNo);

            // 审计
            auditLogService.logAction("RECYCLE_ORDER_BATCH_SUBMIT", "RECYCLE_ORDER", orderNo,
                    "title=" + cartItem.getTitle() + ",estimatedPrice=" + cartItem.getEstimatedPrice());

            // 事件
            eventOutbox.publish(new RecycleOrderSubmitted(orderNo, user.getId(), cartItem.getWearScore()));
            eventOutbox.publish(new RecyclePointsPosted(orderNo, user.getId(), points));

            // 标记回收车条目为已提交
            cartItem.setStatus("SUBMITTED");
            cartItem.setUpdatedAt(LocalDateTime.now());
            recycleCartItemRepository.save(cartItem);
        }

        // 更新用户积分 + 会员等级
        user.setPoints((user.getPoints() == null ? 0 : user.getPoints()) + totalPoints);
        userAccountRepository.save(user);
        memberLevelService.recalculate(user);

        // 通知
        notificationService.push(userId, "RECYCLE_ORDER_BATCH_SUBMITTED", "回收订单已提交",
                "已提交 " + orderNos.size() + " 件旧物，共估价 ¥" + totalPrice.setScale(2).toPlainString()
                        + "，预计获得 " + totalPoints + " 积分。", null, null);

        Map<String, Object> result = new HashMap<>();
        result.put("submittedCount", orderNos.size());
        result.put("orderNumbers", orderNos);
        result.put("totalEstimatedPrice", totalPrice);
        result.put("totalPoints", totalPoints);
        return result;
    }

    static Map<String, Object> toRow(RecycleCartItem item) {
        Map<String, Object> row = new HashMap<>();
        row.put("cartItemId", item.getId());
        row.put("barcode", item.getBarcode());
        row.put("title", item.getTitle());
        row.put("author", item.getAuthor());
        row.put("publisher", item.getPublisher());
        row.put("publishYear", item.getPublishYear());
        row.put("category", item.getCategory());
        row.put("wearScore", item.getWearScore());
        row.put("estimatedPrice", item.getEstimatedPrice());
        row.put("grade", item.getGrade());
        row.put("source", item.getSource());
        row.put("status", item.getStatus());
        row.put("createdAt", item.getCreatedAt());
        return row;
    }
}
