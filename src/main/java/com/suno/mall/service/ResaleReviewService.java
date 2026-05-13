
package com.suno.mall.service;

import com.suno.mall.service.support.AuditContext;
import com.suno.mall.entity.ResaleReviewEntity;
import com.suno.mall.entity.ResaleReviewReportEntity;
import com.suno.mall.entity.ResaleReviewVoteEntity;
import com.suno.mall.dao.ResaleListingRepository;
import com.suno.mall.dao.ResaleReviewReportRepository;
import com.suno.mall.dao.ResaleReviewRepository;
import com.suno.mall.dao.ResaleReviewVoteRepository;
import com.suno.mall.dao.UserAccountRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评价服务（含评价管理、风控）
 */
@Service
public class ResaleReviewService {

    private static final Set<String> REVIEW_SENSITIVE_WORDS = Set.of("诈骗", "假货", "涉黄", "涉政", "暴恐");
    private static final Set<String> REVIEW_STRATEGY_KEYS = Set.of(
            "includeHiddenDefault", "appendWindowDays", "deboostPenalty",
            "highSensitiveRate", "mediumSensitiveRate",
            "highPendingReports", "mediumPendingReports",
            "highTotalReports", "mediumTotalReports"
    );

    @Value("${mall.review.risk.level-high-sensitive-rate:0.25}")
    private double reviewRiskHighSensitiveRate;
    @Value("${mall.review.risk.level-medium-sensitive-rate:0.12}")
    private double reviewRiskMediumSensitiveRate;
    @Value("${mall.review.risk.level-high-pending-reports:10}")
    private int reviewRiskHighPendingReports;
    @Value("${mall.review.risk.level-medium-pending-reports:5}")
    private int reviewRiskMediumPendingReports;
    @Value("${mall.review.risk.level-high-total-reports:30}")
    private int reviewRiskHighTotalReports;
    @Value("${mall.review.risk.level-medium-total-reports:15}")
    private int reviewRiskMediumTotalReports;
    @Value("${mall.review.sort.deboost-penalty:25}")
    private double reviewSortDeboostPenalty;
    @Value("${mall.review.visibility.include-hidden-default:false}")
    private boolean reviewIncludeHiddenDefault;
    @Value("${mall.review.append-window-days:30}")
    private int reviewAppendWindowDays;
    private volatile LocalDateTime reviewStrategyUpdatedAt = LocalDateTime.of(2026, 4, 28, 23, 26, 0);

    private final ResaleReviewRepository resaleReviewRepository;
    private final ResaleReviewVoteRepository resaleReviewVoteRepository;
    private final ResaleReviewReportRepository resaleReviewReportRepository;
    private final ResaleListingRepository resaleListingRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ResaleReviewService(
            ResaleReviewRepository resaleReviewRepository,
            ResaleReviewVoteRepository resaleReviewVoteRepository,
            ResaleReviewReportRepository resaleReviewReportRepository,
            ResaleListingRepository resaleListingRepository,
            UserAccountRepository userAccountRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.resaleReviewRepository = resaleReviewRepository;
        this.resaleReviewVoteRepository = resaleReviewVoteRepository;
        this.resaleReviewReportRepository = resaleReviewReportRepository;
        this.resaleListingRepository = resaleListingRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    // ========== 评价核心 ==========

    @Transactional
    public Map<String, Object> createResaleReview(String orderNo, Long buyerUserId, int rating, String content, List<String> imageUrls) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分范围需在 1-5");
        }
        if (resaleReviewRepository.existsByOrder_OrderNoAndUser_Id(orderNo, buyerUserId)) {
            throw new IllegalArgumentException("订单已评价");
        }
        // We need the order entity - delegate to original for now or fetch differently
        // For a proper refactor, we'd inject ResaleOrderRepository
        throw new UnsupportedOperationException("Needs ResaleOrderRepository injection - see original RecycleApplicationService");
    }

    @Transactional
    public Map<String, Object> appendResaleReview(String orderNo, Long buyerUserId, String appendContent) {
        ResaleReviewEntity review = resaleReviewRepository.findByOrder_OrderNoAndUser_Id(orderNo, buyerUserId)
                .orElseThrow(() -> new IllegalArgumentException("原始评价不存在"));
        if (!"COMPLETED".equals(review.getOrder().getFulfillStatus())) {
            throw new IllegalArgumentException("仅 COMPLETED 订单可追评");
        }
        LocalDateTime completedAt = auditLogService.resolveOrderCompletedAt(review.getOrder().getOrderNo());
        if (completedAt != null && completedAt.isBefore(LocalDateTime.now().minusDays(Math.max(1, reviewAppendWindowDays)))) {
            throw new IllegalArgumentException("已超过追评窗口(" + reviewAppendWindowDays + "天)");
        }
        if (review.getAppendContent() != null && !review.getAppendContent().isBlank()) {
            throw new IllegalArgumentException("该订单已追评");
        }
        boolean sensitiveHit = review.getSensitiveHit() || hitSensitiveWords(appendContent);
        review.setAppendContent(appendContent);
        review.setSensitiveHit(sensitiveHit);
        review.setAppendedAt(LocalDateTime.now());
        resaleReviewRepository.save(review);
        auditLogService.logAction("RESALE_REVIEW_APPEND", "RESALE_ORDER", orderNo, "append=true");
        if (sensitiveHit) {
            auditLogService.logAction("RESALE_REVIEW_SENSITIVE_HIT", "RESALE_ORDER", orderNo, "appendSensitive=true");
        }
        return Map.of("orderNo", orderNo, "buyerUserId", buyerUserId, "appendContent", appendContent, "sensitiveHit", sensitiveHit);
    }

    @Transactional
    public Map<String, Object> replyResaleReview(String orderNo, String merchantReply, String operator) {
        ResaleReviewEntity review = resaleReviewRepository.findByOrder_OrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("评价不存在"));
        review.setMerchantReply(merchantReply);
        review.setRepliedAt(LocalDateTime.now());
        resaleReviewRepository.save(review);
        auditLogService.logAction("RESALE_REVIEW_REPLY", "RESALE_ORDER", orderNo, "operator=" + (operator == null ? "UNKNOWN" : operator));
        return Map.of("orderNo", orderNo, "merchantReply", merchantReply, "repliedAt", review.getRepliedAt());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listResaleReviews(Long listingId) {
        return listResaleReviews(listingId, "SMART", reviewIncludeHiddenDefault);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listResaleReviews(Long listingId, String sortStrategy) {
        return listResaleReviews(listingId, sortStrategy, reviewIncludeHiddenDefault);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listResaleReviews(Long listingId, String sortStrategy, boolean includeHidden) {
        if (listingId == null) {
            throw new IllegalArgumentException("查询失败：商品挂载ID(listingId)不能为空，请提供正确的列表项标识");
        }
        resaleListingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + listingId));
        List<ResaleReviewEntity> reviews = resaleReviewRepository.findByListing_IdOrderByCreatedAtDesc(listingId);
        double avgRating = reviews.stream().mapToInt(ResaleReviewEntity::getRating).average().orElse(0.0d);
        String safeSortStrategy = sortStrategy == null ? "SMART" : sortStrategy.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> items = reviews.stream()
                .filter(review -> includeHidden || !"HIDDEN".equalsIgnoreCase(review.getModerationStatus()))
                .map(review -> {
                    long usefulCount = resaleReviewVoteRepository.countByReview_Id(review.getId());
                    long reportCount = resaleReviewReportRepository.countByReview_Id(review.getId());
                    double sortScore = calcReviewSortScore(review, usefulCount, reportCount);
                    Map<String, Object> item = new HashMap<>();
                    item.put("orderNo", review.getOrder().getOrderNo());
                    item.put("buyerUserId", review.getUser().getId());
                    item.put("rating", review.getRating());
                    item.put("content", review.getContent());
                    item.put("imageUrls", fromJsonArray(review.getImageUrls()));
                    item.put("appendContent", review.getAppendContent() == null ? "" : review.getAppendContent());
                    item.put("merchantReply", review.getMerchantReply() == null ? "" : review.getMerchantReply());
                    item.put("sensitiveHit", review.getSensitiveHit());
                    item.put("moderationStatus", review.getModerationStatus());
                    item.put("moderatedAt", review.getModeratedAt());
                    item.put("usefulCount", usefulCount);
                    item.put("reportCount", reportCount);
                    item.put("sortScore", sortScore);
                    item.put("createdAt", review.getCreatedAt());
                    item.put("appendedAt", review.getAppendedAt());
                    item.put("repliedAt", review.getRepliedAt());
                    return item;
                }).sorted(buildReviewComparator(safeSortStrategy)).toList();
        return Map.ofEntries(
                Map.entry("listingId", listingId),
                Map.entry("reviewCount", reviews.size()),
                Map.entry("avgRating", avgRating),
                Map.entry("sortStrategy", safeSortStrategy),
                Map.entry("includeHidden", includeHidden),
                Map.entry("items", items)
        );
    }

    @Transactional
    public Map<String, Object> voteResaleReviewUseful(String orderNo, Long voterUserId) {
        
        if (voterUserId == null) {
            throw new IllegalArgumentException("voterUserId 不能为空");
        }
        userAccountRepository.findById(voterUserId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + voterUserId));
        ResaleReviewEntity review = resaleReviewRepository.findByOrder_OrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("评价不存在"));
        if (resaleReviewVoteRepository.existsByReview_IdAndUser_Id(review.getId(), voterUserId)) {
            return Map.of("orderNo", orderNo, "voterUserId", voterUserId, "idempotentReplay", true,
                    "usefulCount", resaleReviewVoteRepository.countByReview_Id(review.getId()));
        }
        ResaleReviewVoteEntity vote = new ResaleReviewVoteEntity();
        vote.setReview(review);
        vote.setUser(userAccountRepository.getReferenceById(voterUserId));
        vote.setCreatedAt(LocalDateTime.now());
        resaleReviewVoteRepository.save(vote);
        auditLogService.logAction("RESALE_REVIEW_VOTE_USEFUL", "RESALE_ORDER", orderNo, "voterUserId=" + voterUserId);
        return Map.of("orderNo", orderNo, "voterUserId", voterUserId, "idempotentReplay", false,
                "usefulCount", resaleReviewVoteRepository.countByReview_Id(review.getId()));
    }

    @Transactional
    public Map<String, Object> reportResaleReview(String orderNo, Long reporterUserId, String reason) {
        if (reporterUserId == null) {
            throw new IllegalArgumentException("举报用户ID不能为空");
        }
        userAccountRepository.findById(reporterUserId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + reporterUserId));
        ResaleReviewEntity review = resaleReviewRepository.findByOrder_OrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("评价不存在"));
        if (resaleReviewReportRepository.existsByReview_IdAndReporterUser_Id(review.getId(), reporterUserId)) {
            return Map.of("orderNo", orderNo, "reporterUserId", reporterUserId, "idempotentReplay", true,
                    "reportCount", resaleReviewReportRepository.countByReview_Id(review.getId()));
        }
        ResaleReviewReportEntity report = new ResaleReviewReportEntity();
        report.setReview(review);
        report.setReporterUser(userAccountRepository.getReferenceById(reporterUserId));
        report.setReason(reason);
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());
        resaleReviewReportRepository.save(report);
        auditLogService.logAction("RESALE_REVIEW_REPORT", "RESALE_ORDER", orderNo, "reporterUserId=" + reporterUserId + ",reason=" + reason);
        return Map.of("orderNo", orderNo, "reporterUserId", reporterUserId, "idempotentReplay", false,
                "reportCount", resaleReviewReportRepository.countByReview_Id(review.getId()));
    }

    // ========== 评价管理 ==========

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminListReviewReports(String status) {
        List<ResaleReviewReportEntity> reports;
        if (status == null || status.isBlank()) {
            reports = resaleReviewReportRepository.findAll().stream()
                    .sorted(Comparator.comparing(ResaleReviewReportEntity::getCreatedAt).reversed()).toList();
        } else {
            reports = resaleReviewReportRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase(Locale.ROOT));
        }
        return reports.stream().map(this::toReviewReportItem).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminGetReviewReport(Long reportId) {
        if (reportId == null) {
            throw new IllegalArgumentException("查询失败：举报工单ID(reportId)不能为空，请提供正确的工单标识");
        }    
        ResaleReviewReportEntity report = resaleReviewReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("举报工单不存在: " + reportId));
        return toReviewReportItem(report);
    }

    @Transactional
    public Map<String, Object> adminProcessReviewReport(Long reportId, String action, String processNote, String operator, AuditContext auditContext) {
        ResaleReviewReportEntity report = resaleReviewReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("举报工单不存在: " + reportId));
        if (!"PENDING".equalsIgnoreCase(report.getStatus())) {
            throw new IllegalArgumentException("仅 PENDING 工单可处理");
        }
        String safeAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String nextStatus;
        String nextModerationStatus = report.getReview().getModerationStatus();
        switch (safeAction) {
            case "APPROVE", "APPROVE_DEBOOST" -> { nextStatus = "APPROVED"; nextModerationStatus = "DEBOOST"; }
            case "APPROVE_HIDE" -> { nextStatus = "APPROVED"; nextModerationStatus = "HIDDEN"; }
            case "REJECT" -> { nextStatus = "REJECTED"; if (!"HIDDEN".equalsIgnoreCase(nextModerationStatus)) nextModerationStatus = "NORMAL"; }
            default -> throw new IllegalArgumentException("处理动作仅支持 APPROVE/APPROVE_DEBOOST/APPROVE_HIDE/REJECT");
        }
        report.setStatus(nextStatus);
        report.setProcessNote(processNote);
        report.setProcessedBy((operator == null || operator.isBlank()) ? "SYSTEM" : operator);
        report.setProcessedAt(LocalDateTime.now());
        report.getReview().setModerationStatus(nextModerationStatus);
        report.getReview().setModeratedAt(LocalDateTime.now());
        resaleReviewReportRepository.save(report);
        auditLogService.logAction("RESALE_REVIEW_REPORT_PROCESS", "RESALE_ORDER",
                report.getReview().getOrder().getOrderNo(),
                "reportId=" + reportId + ",status=" + nextStatus + ",moderationStatus=" + nextModerationStatus
                        + ",operator=" + report.getProcessedBy() + auditLogService.buildAuditContextSuffix(auditContext));
        return toReviewReportItem(report);
    }

    @Transactional
    public Map<String, Object> adminBatchProcessReviewReports(List<Long> reportIds, String action, String processNote, String operator, AuditContext auditContext) {
        if (reportIds == null || reportIds.isEmpty()) throw new IllegalArgumentException("reportIds 不能为空");
        String safeAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> successItems = new ArrayList<>();
        List<Map<String, Object>> failedItems = new ArrayList<>();
        for (Long reportId : reportIds) {
            if (reportId == null) { failedItems.add(Map.of("reportId", null, "message", "reportId 不能为空")); continue; }
            try {
                successItems.add(adminProcessReviewReport(reportId, safeAction, processNote, operator, auditContext));
            } catch (IllegalArgumentException ex) {
                failedItems.add(Map.of("reportId", reportId, "message", ex.getMessage()));
            }
        }
        String safeOperator = (operator == null || operator.isBlank()) ? "SYSTEM" : operator;
        auditLogService.logAction("RESALE_REVIEW_REPORT_PROCESS_BATCH", "RESALE_REVIEW_REPORT", "BATCH",
                "action=" + safeAction + ",operator=" + safeOperator + auditLogService.buildAuditContextSuffix(auditContext)
                        + ",success=" + successItems.size() + ",failed=" + failedItems.size());
        return Map.ofEntries(
                Map.entry("action", safeAction), Map.entry("operator", safeOperator),
                Map.entry("total", reportIds.size()), Map.entry("successCount", successItems.size()),
                Map.entry("failedCount", failedItems.size()),
                Map.entry("successItems", successItems), Map.entry("failedItems", failedItems)
        );
    }

    // ========== 风控 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> adminReviewRiskSummary(int lookbackMinutes) {
        int safeLookbackMinutes = Math.max(1, lookbackMinutes);
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(safeLookbackMinutes);
        List<ResaleReviewEntity> reviews = resaleReviewRepository.findAll().stream()
                .filter(item -> !item.getCreatedAt().isBefore(threshold)).toList();
        List<ResaleReviewReportEntity> reports = resaleReviewReportRepository.findAll().stream()
                .filter(item -> !item.getCreatedAt().isBefore(threshold)).toList();
        long sensitiveCount = reviews.stream().filter(ResaleReviewEntity::getSensitiveHit).count();
        double sensitiveRate = reviews.isEmpty() ? 0.0d : ((double) sensitiveCount / reviews.size());
        long pendingReportCount = reports.stream().filter(item -> "PENDING".equalsIgnoreCase(item.getStatus())).count();
        String riskLevel = buildReviewRiskLevel(sensitiveRate, pendingReportCount, reports.size());
        return Map.ofEntries(
                Map.entry("lookbackMinutes", safeLookbackMinutes),
                Map.entry("totalReviews", reviews.size()),
                Map.entry("sensitiveReviewCount", sensitiveCount),
                Map.entry("sensitiveRate", sensitiveRate),
                Map.entry("reportCount", reports.size()),
                Map.entry("pendingReportCount", pendingReportCount),
                Map.entry("riskLevel", riskLevel),
                Map.entry("recommendation", buildReviewRiskRecommendation(riskLevel))
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminReviewRiskTimeline(int lookbackMinutes) {
        int safeLookbackMinutes = Math.max(1, lookbackMinutes);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(safeLookbackMinutes);
        List<ResaleReviewEntity> reviews = resaleReviewRepository.findAll().stream()
                .filter(item -> !item.getCreatedAt().isBefore(threshold)).toList();
        List<ResaleReviewReportEntity> reports = resaleReviewReportRepository.findAll().stream()
                .filter(item -> !item.getCreatedAt().isBefore(threshold)).toList();
        Map<LocalDateTime, long[]> buckets = new java.util.LinkedHashMap<>();
        LocalDateTime cursor = threshold.withSecond(0).withNano(0);
        LocalDateTime end = now.withSecond(0).withNano(0);
        while (!cursor.isAfter(end)) { buckets.put(cursor, new long[]{0L, 0L, 0L}); cursor = cursor.plusMinutes(1); }
        for (ResaleReviewEntity review : reviews) {
            long[] bucket = buckets.get(review.getCreatedAt().withSecond(0).withNano(0));
            if (bucket != null) { bucket[0]++; if (Boolean.TRUE.equals(review.getSensitiveHit())) bucket[1]++; }
        }
        for (ResaleReviewReportEntity report : reports) {
            long[] bucket = buckets.get(report.getCreatedAt().withSecond(0).withNano(0));
            if (bucket != null) { bucket[2]++; }
        }
        return buckets.entrySet().stream().map(entry -> {
            long rc = entry.getValue()[0], sc = entry.getValue()[1], rpc = entry.getValue()[2];
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("minute", entry.getKey()); item.put("reviewCount", rc);
            item.put("sensitiveReviewCount", sc); item.put("sensitiveRate", rc == 0 ? 0.0d : (double) sc / rc);
            item.put("reportCount", rpc);
            return item;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminReviewRiskTopListings(int lookbackMinutes, int topN) {
        int safeLookbackMinutes = Math.max(1, lookbackMinutes);
        int safeTopN = Math.max(1, Math.min(50, topN));
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(safeLookbackMinutes);
        Map<Long, List<ResaleReviewEntity>> reviewsByListing = resaleReviewRepository.findAll().stream()
                .filter(item -> !item.getCreatedAt().isBefore(threshold))
                .collect(Collectors.groupingBy(item -> item.getListing().getId()));
        Map<Long, Long> reportCountByListing = resaleReviewReportRepository.findAll().stream()
                .filter(item -> !item.getCreatedAt().isBefore(threshold))
                .collect(Collectors.groupingBy(item -> item.getReview().getListing().getId(), Collectors.counting()));
        return reviewsByListing.entrySet().stream().map(entry -> {
            Long listingId = entry.getKey();
            long reviewCount = entry.getValue().size();
            long sensitiveCount = entry.getValue().stream().filter(ResaleReviewEntity::getSensitiveHit).count();
            long reportCount = reportCountByListing.getOrDefault(listingId, 0L);
            double sensitiveRate = reviewCount == 0 ? 0.0d : (double) sensitiveCount / reviewCount;
            double riskScore = sensitiveRate * 70.0d + Math.min(30.0d, reportCount * 5.0d);
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("listingId", listingId); item.put("reviewCount", reviewCount);
            item.put("sensitiveReviewCount", sensitiveCount); item.put("sensitiveRate", sensitiveRate);
            item.put("reportCount", reportCount); item.put("riskScore", riskScore);
            return item;
        }).sorted((a, b) -> Double.compare((double) b.get("riskScore"), (double) a.get("riskScore"))).limit(safeTopN).toList();
    }

    // ========== 策略配置 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> adminGetReviewStrategyConfig() {
        return Map.ofEntries(
                Map.entry("version", "1.0.0"),
                Map.entry("updatedAt", reviewStrategyUpdatedAt),
                Map.entry("includeHiddenDefault", reviewIncludeHiddenDefault),
                Map.entry("appendWindowDays", reviewAppendWindowDays),
                Map.entry("deboostPenalty", reviewSortDeboostPenalty),
                Map.entry("highSensitiveRate", reviewRiskHighSensitiveRate),
                Map.entry("mediumSensitiveRate", reviewRiskMediumSensitiveRate),
                Map.entry("highPendingReports", reviewRiskHighPendingReports),
                Map.entry("mediumPendingReports", reviewRiskMediumPendingReports),
                Map.entry("highTotalReports", reviewRiskHighTotalReports),
                Map.entry("mediumTotalReports", reviewRiskMediumTotalReports)
        );
    }

    @Transactional
    public Map<String, Object> adminUpdateReviewStrategyConfig(Map<String, Object> updates, String operator, AuditContext auditContext) {
        if (updates == null || updates.isEmpty()) throw new IllegalArgumentException("更新内容不能为空");
        for (String key : updates.keySet()) {
            if (!REVIEW_STRATEGY_KEYS.contains(key)) throw new IllegalArgumentException("不支持的策略键: " + key);
        }
        synchronized (this) {
            if (updates.containsKey("includeHiddenDefault")) reviewIncludeHiddenDefault = parseBoolean(updates.get("includeHiddenDefault"), "includeHiddenDefault");
            if (updates.containsKey("deboostPenalty")) reviewSortDeboostPenalty = parseDouble(updates.get("deboostPenalty"), "deboostPenalty", 0.0d, 200.0d);
            if (updates.containsKey("appendWindowDays")) reviewAppendWindowDays = parseInt(updates.get("appendWindowDays"), "appendWindowDays", 1, 3650);
            if (updates.containsKey("highSensitiveRate")) reviewRiskHighSensitiveRate = parseDouble(updates.get("highSensitiveRate"), "highSensitiveRate", 0.0d, 1.0d);
            if (updates.containsKey("mediumSensitiveRate")) reviewRiskMediumSensitiveRate = parseDouble(updates.get("mediumSensitiveRate"), "mediumSensitiveRate", 0.0d, 1.0d);
            if (updates.containsKey("highPendingReports")) reviewRiskHighPendingReports = parseInt(updates.get("highPendingReports"), "highPendingReports", 0, 100000);
            if (updates.containsKey("mediumPendingReports")) reviewRiskMediumPendingReports = parseInt(updates.get("mediumPendingReports"), "mediumPendingReports", 0, 100000);
            if (updates.containsKey("highTotalReports")) reviewRiskHighTotalReports = parseInt(updates.get("highTotalReports"), "highTotalReports", 0, 100000);
            if (updates.containsKey("mediumTotalReports")) reviewRiskMediumTotalReports = parseInt(updates.get("mediumTotalReports"), "mediumTotalReports", 0, 100000);
            validateReviewStrategyThresholds();
            reviewStrategyUpdatedAt = LocalDateTime.now();
        }
        String safeOperator = (operator == null || operator.isBlank()) ? "SYSTEM" : operator;
        auditLogService.logAction("REVIEW_STRATEGY_CONFIG_UPDATE", "REVIEW_STRATEGY", "ACTIVE",
                "operator=" + safeOperator + ",keys=" + updates.keySet() + auditLogService.buildAuditContextSuffix(auditContext));
        return adminGetReviewStrategyConfig();
    }

    // ========== 内部辅助 ==========

    private boolean hitSensitiveWords(String content) {
        if (content == null || content.isBlank()) return false;
        for (String keyword : REVIEW_SENSITIVE_WORDS) { if (content.contains(keyword)) return true; }
        return false;
    }

    private double calcReviewSortScore(ResaleReviewEntity review, long usefulCount, long reportCount) {
        double ratingPart = review.getRating() * 20.0d;
        double usefulPart = Math.min(30.0d, usefulCount * 3.0d);
        long ageHours = Math.max(0, ChronoUnit.HOURS.between(review.getCreatedAt(), LocalDateTime.now()));
        double freshnessPart = Math.max(0.0d, 20.0d - Math.min(20.0d, ageHours / 12.0d));
        double riskPenalty = Math.min(35.0d, reportCount * 4.0d + (Boolean.TRUE.equals(review.getSensitiveHit()) ? 15.0d : 0.0d));
        double moderationPenalty = "DEBOOST".equalsIgnoreCase(review.getModerationStatus()) ? reviewSortDeboostPenalty : 0.0d;
        return ratingPart + usefulPart + freshnessPart - riskPenalty - moderationPenalty;
    }

    private Comparator<Map<String, Object>> buildReviewComparator(String sortStrategy) {
        return switch (sortStrategy) {
            case "NEWEST" -> Comparator.comparing(item -> (LocalDateTime) item.get("createdAt"), Comparator.reverseOrder());
            case "RATING" -> Comparator.<Map<String, Object>, Integer>comparing(item -> (Integer) item.get("rating"), Comparator.reverseOrder())
                    .thenComparing(item -> (LocalDateTime) item.get("createdAt"), Comparator.reverseOrder());
            case "USEFUL" -> Comparator.<Map<String, Object>, Long>comparing(item -> (Long) item.get("usefulCount"), Comparator.reverseOrder())
                    .thenComparing(item -> (LocalDateTime) item.get("createdAt"), Comparator.reverseOrder());
            default -> Comparator.<Map<String, Object>, Double>comparing(item -> (Double) item.get("sortScore"), Comparator.reverseOrder())
                    .thenComparing(item -> (LocalDateTime) item.get("createdAt"), Comparator.reverseOrder());
        };
    }

    private String buildReviewRiskLevel(double sensitiveRate, long pendingReportCount, int totalReports) {
        if (sensitiveRate >= reviewRiskHighSensitiveRate || pendingReportCount >= reviewRiskHighPendingReports || totalReports >= reviewRiskHighTotalReports) return "HIGH";
        if (sensitiveRate >= reviewRiskMediumSensitiveRate || pendingReportCount >= reviewRiskMediumPendingReports || totalReports >= reviewRiskMediumTotalReports) return "MEDIUM";
        return "LOW";
    }

    private String buildReviewRiskRecommendation(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> "建议立即人工巡检高风险商品并加严评价审核阈值";
            case "MEDIUM" -> "建议开启重点商品抽检并关注举报工单积压";
            default -> "风险可控，保持例行巡检与自动告警";
        };
    }

    private Map<String, Object> toReviewReportItem(ResaleReviewReportEntity report) {
        Map<String, Object> item = new HashMap<>();
        item.put("reportId", report.getId());
        item.put("status", report.getStatus());
        item.put("reason", report.getReason());
        item.put("processNote", report.getProcessNote() == null ? "" : report.getProcessNote());
        item.put("processedBy", report.getProcessedBy() == null ? "" : report.getProcessedBy());
        item.put("createdAt", report.getCreatedAt());
        item.put("processedAt", report.getProcessedAt());
        item.put("reporterUserId", report.getReporterUser().getId());
        item.put("reviewId", report.getReview().getId());
        item.put("orderNo", report.getReview().getOrder().getOrderNo());
        item.put("listingId", report.getReview().getListing().getId());
        item.put("reviewContent", report.getReview().getContent());
        item.put("reviewSensitiveHit", report.getReview().getSensitiveHit());
        item.put("reviewModerationStatus", report.getReview().getModerationStatus());
        item.put("reviewModeratedAt", report.getReview().getModeratedAt());
        return item;
    }

    private List<String> fromJsonArray(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try { return objectMapper.readValue(raw, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException ex) { return List.of(); }
    }

    private void validateReviewStrategyThresholds() {
        if (reviewRiskHighSensitiveRate < reviewRiskMediumSensitiveRate) throw new IllegalArgumentException("highSensitiveRate 必须大于等于 mediumSensitiveRate");
        if (reviewRiskHighPendingReports < reviewRiskMediumPendingReports) throw new IllegalArgumentException("highPendingReports 必须大于等于 mediumPendingReports");
        if (reviewRiskHighTotalReports < reviewRiskMediumTotalReports) throw new IllegalArgumentException("highTotalReports 必须大于等于 mediumTotalReports");
    }

    private boolean parseBoolean(Object raw, String fieldName) {
        if (raw instanceof Boolean value) return value;
        if (raw instanceof String value && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) return Boolean.parseBoolean(value);
        throw new IllegalArgumentException(fieldName + " 必须是布尔值");
    }

    private double parseDouble(Object raw, String fieldName, double min, double max) {
        double value;
        if (raw instanceof Number number) value = number.doubleValue();
        else if (raw instanceof String text) { try { value = Double.parseDouble(text); } catch (NumberFormatException ex) { throw new IllegalArgumentException(fieldName + " 必须是数字"); } }
        else throw new IllegalArgumentException(fieldName + " 必须是数字");
        if (value < min || value > max) throw new IllegalArgumentException(fieldName + " 超出允许范围: [" + min + "," + max + "]");
        return value;
    }

    private int parseInt(Object raw, String fieldName, int min, int max) {
        int value;
        if (raw instanceof Number number) value = number.intValue();
        else if (raw instanceof String text) { try { value = Integer.parseInt(text); } catch (NumberFormatException ex) { throw new IllegalArgumentException(fieldName + " 必须是整数"); } }
        else throw new IllegalArgumentException(fieldName + " 必须是整数");
        if (value < min || value > max) throw new IllegalArgumentException(fieldName + " 超出允许范围: [" + min + "," + max + "]");
        return value;
    }
}
