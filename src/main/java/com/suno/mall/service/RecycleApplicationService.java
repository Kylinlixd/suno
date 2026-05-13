package com.suno.mall.service;

import com.recycle.mall.service.support.AuditContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecycleApplicationService {

    private final RecycleOrderService recycleOrderService;
    private final ResaleListingService resaleListingService;
    private final ResaleOrderService resaleOrderService;
    private final ResaleReviewService resaleReviewService;
    private final PaymentReplayService paymentReplayService;
    private final ConfigCenterService configCenterService;
    private final AuditLogService auditLogService;

    public RecycleApplicationService(
            RecycleOrderService recycleOrderService,
            ResaleListingService resaleListingService,
            ResaleOrderService resaleOrderService,
            ResaleReviewService resaleReviewService,
            PaymentReplayService paymentReplayService,
            ConfigCenterService configCenterService,
            AuditLogService auditLogService
    ) {
        this.recycleOrderService = recycleOrderService;
        this.resaleListingService = resaleListingService;
        this.resaleOrderService = resaleOrderService;
        this.resaleReviewService = resaleReviewService;
        this.paymentReplayService = paymentReplayService;
        this.configCenterService = configCenterService;
        this.auditLogService = auditLogService;
    }

    // ==================== 回收订单 ====================

    @Transactional
    public Map<String, Object> createRecycleOrder(Long userId, String snCode, String imageUrl, int wearScore, int recycleCount) {
        return recycleOrderService.createRecycleOrder(userId, snCode, imageUrl, wearScore, recycleCount);
    }

    public String queryLogisticsStatus(String trackingNo) {
        return recycleOrderService.queryLogisticsStatus(trackingNo);
    }

    public List<Map<String, Object>> listRecycleOrders() {
        return recycleOrderService.listRecycleOrders();
    }

    @Transactional
    public Map<String, Object> transitionOrder(String orderNo, String action, String reviewedGrade) {
        return recycleOrderService.transitionOrder(orderNo, action, reviewedGrade);
    }

    // ==================== 二销商品 ====================

    @Transactional
    public Map<String, Object> publishResaleListing(String recycleOrderNo, BigDecimal salePrice, int stock) {
        return resaleListingService.publishResaleListing(recycleOrderNo, salePrice, stock);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listResaleListings() {
        return resaleListingService.listResaleListings();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listResaleListings(String grade, String sortBy, String sortOrder, Integer minStock) {
        return resaleListingService.listResaleListings(grade, sortBy, sortOrder, minStock);
    }

    @Transactional
    public Map<String, Object> addFavoriteListing(Long userId, Long listingId) {
        return resaleListingService.addFavoriteListing(userId, listingId);
    }

    @Transactional
    public Map<String, Object> removeFavoriteListing(Long userId, Long listingId) {
        return resaleListingService.removeFavoriteListing(userId, listingId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFavoriteListings(Long userId) {
        return resaleListingService.listFavoriteListings(userId);
    }

    // ==================== 二销订单 ====================

    @Transactional
    public Map<String, Object> createResaleOrder(Long buyerUserId, Long listingId) {
        return resaleOrderService.createResaleOrder(buyerUserId, listingId);
    }

    @Transactional
    public Map<String, Object> markResaleOrderPaid(String orderNo) {
        return resaleOrderService.markResaleOrderPaid(orderNo);
    }

    @Transactional
    public Map<String, Object> markResaleOrderPaidWithIdempotency(String orderNo, String idempotencyKey) {
        return resaleOrderService.markResaleOrderPaidWithIdempotency(orderNo, idempotencyKey);
    }

    @Transactional
    public Map<String, Object> deliverResaleOrder(String orderNo) {
        return resaleOrderService.deliverResaleOrder(orderNo, AuditContext.empty());
    }

    @Transactional
    public Map<String, Object> deliverResaleOrder(String orderNo, AuditContext auditContext) {
        return resaleOrderService.deliverResaleOrder(orderNo, auditContext);
    }

    @Transactional
    public Map<String, Object> confirmResaleOrderReceipt(String orderNo, Long buyerUserId) {
        return resaleOrderService.confirmResaleOrderReceipt(orderNo, buyerUserId);
    }

    @Transactional
    public Map<String, Object> cancelUnpaidResaleOrder(String orderNo) {
        return resaleOrderService.cancelUnpaidResaleOrder(orderNo);
    }

    @Transactional
    public Map<String, Object> refundPaidResaleOrder(String orderNo) {
        return resaleOrderService.refundPaidResaleOrder(orderNo, AuditContext.empty());
    }

    @Transactional
    public Map<String, Object> refundPaidResaleOrder(String orderNo, AuditContext auditContext) {
        return resaleOrderService.refundPaidResaleOrder(orderNo, auditContext);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> queryResaleOrderTrack(String orderNo, Long buyerUserId) {
        return resaleOrderService.queryResaleOrderTrack(orderNo, buyerUserId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listBuyerResaleOrders(Long buyerUserId, String payStatus, String fulfillStatus,
            String sortBy, String sortOrder, Integer limit, Integer page, Integer size) {
        return resaleOrderService.listBuyerResaleOrders(buyerUserId, payStatus, fulfillStatus, sortBy, sortOrder, limit, page, size);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getResaleOrderStatusDictionary() {
        return resaleOrderService.getResaleOrderStatusDictionary();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summarizeBuyerResaleOrders(Long buyerUserId, Integer lookbackDays) {
        return resaleOrderService.summarizeBuyerResaleOrders(buyerUserId, lookbackDays);
    }

    // ==================== 评价 ====================

    @Transactional
    public Map<String, Object> createResaleReview(String orderNo, Long buyerUserId, int rating, String content, List<String> imageUrls) {
        return resaleReviewService.createResaleReview(orderNo, buyerUserId, rating, content, imageUrls);
    }

    @Transactional
    public Map<String, Object> appendResaleReview(String orderNo, Long buyerUserId, String appendContent) {
        return resaleReviewService.appendResaleReview(orderNo, buyerUserId, appendContent);
    }

    @Transactional
    public Map<String, Object> replyResaleReview(String orderNo, String merchantReply, String operator) {
        return resaleReviewService.replyResaleReview(orderNo, merchantReply, operator);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listResaleReviews(Long listingId) {
        return resaleReviewService.listResaleReviews(listingId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listResaleReviews(Long listingId, String sortStrategy) {
        return resaleReviewService.listResaleReviews(listingId, sortStrategy);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listResaleReviews(Long listingId, String sortStrategy, boolean includeHidden) {
        return resaleReviewService.listResaleReviews(listingId, sortStrategy, includeHidden);
    }

    @Transactional
    public Map<String, Object> voteResaleReviewUseful(String orderNo, Long voterUserId) {
        return resaleReviewService.voteResaleReviewUseful(orderNo, voterUserId);
    }

    @Transactional
    public Map<String, Object> reportResaleReview(String orderNo, Long reporterUserId, String reason) {
        return resaleReviewService.reportResaleReview(orderNo, reporterUserId, reason);
    }

    // ==================== 评价管理 & 风控 ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminListReviewReports(String status) {
        return resaleReviewService.adminListReviewReports(status);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminGetReviewReport(Long reportId) {
        return resaleReviewService.adminGetReviewReport(reportId);
    }

    @Transactional
    public Map<String, Object> adminProcessReviewReport(Long reportId, String action, String processNote, String operator, AuditContext auditContext) {
        return resaleReviewService.adminProcessReviewReport(reportId, action, processNote, operator, auditContext);
    }

    @Transactional
    public Map<String, Object> adminBatchProcessReviewReports(List<Long> reportIds, String action, String processNote, String operator, AuditContext auditContext) {
        return resaleReviewService.adminBatchProcessReviewReports(reportIds, action, processNote, operator, auditContext);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminReviewRiskSummary(int lookbackMinutes) {
        return resaleReviewService.adminReviewRiskSummary(lookbackMinutes);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminReviewRiskTimeline(int lookbackMinutes) {
        return resaleReviewService.adminReviewRiskTimeline(lookbackMinutes);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminReviewRiskTopListings(int lookbackMinutes, int topN) {
        return resaleReviewService.adminReviewRiskTopListings(lookbackMinutes, topN);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminGetReviewStrategyConfig() {
        return configCenterService.adminGetReviewStrategyConfig();
    }

    @Transactional
    public Map<String, Object> adminUpdateReviewStrategyConfig(Map<String, Object> updates, String operator, AuditContext auditContext) {
        return configCenterService.adminUpdateReviewStrategyConfig(updates, operator, auditContext);
    }

    // ==================== 配置中心 & 字典 ====================

    @Transactional(readOnly = true)
    public Map<String, Object> adminGlobalErrorCodeDictionary() {
        return configCenterService.adminGlobalErrorCodeDictionary();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminAlertNoiseRulesConfig() {
        return configCenterService.adminAlertNoiseRulesConfig();
    }

    @Transactional
    public Map<String, Object> adminUpdateAlertNoiseRulesConfig(Map<String, Object> updates, String operator, AuditContext auditContext) {
        return configCenterService.adminUpdateAlertNoiseRulesConfig(updates, operator, auditContext);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminDegradeActionTypeDictionary() {
        return configCenterService.adminDegradeActionTypeDictionary();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminConfigCenterBundle() {
        return configCenterService.adminConfigCenterBundle();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminConfigCenterBundle(String clientVersion) {
        return configCenterService.adminConfigCenterBundle(clientVersion);
    }

    public Map<String, Object> adminConfigCenterModule(String moduleName, String clientVersion) {
        return configCenterService.adminConfigCenterModule(moduleName, clientVersion);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminConfigCenterModules() {
        return configCenterService.adminConfigCenterModules();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminConfigCenterModuleDiff(Map<String, String> localDigests, String clientVersion) {
        return configCenterService.adminConfigCenterModuleDiff(localDigests, clientVersion);
    }

    // ==================== 支付回调 & 重放 ====================

    @Transactional
    public void logPaymentCallback(String orderNo, String idempotencyKey, String payStatus,
            String nonce, long timestamp, String signature, String callbackStatus,
            String errorMessage, String responseBody, String source) {
        paymentReplayService.logPaymentCallback(orderNo, idempotencyKey, payStatus, nonce, timestamp, signature, callbackStatus, errorMessage, responseBody, source);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pagePaymentCallbackLogs(int page, int size, String callbackStatus) {
        return paymentReplayService.pagePaymentCallbackLogs(page, size, callbackStatus);
    }

    @Transactional
    public Map<String, Object> enqueueReplayTask(Long callbackLogId) {
        return paymentReplayService.enqueueReplayTask(callbackLogId);
    }

    @Transactional
    public Map<String, Object> consumeReplayTasks(int maxCount) {
        return paymentReplayService.consumeReplayTasks(maxCount);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pageReplayTasks(int page, int size, String status) {
        return paymentReplayService.pageReplayTasks(page, size, status);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> replayTaskSummary() {
        return paymentReplayService.replayTaskSummary();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> replayQueryAuditActions(String requestId, String lang) {
        return paymentReplayService.replayQueryAuditActions(requestId, lang);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> replayTaskHealth(String requestId) {
        return paymentReplayService.replayTaskHealth(requestId);
    }

    @Transactional
    public Map<String, Object> replayTaskDiagnosis(String requestId) {
        return paymentReplayService.replayTaskDiagnosis(requestId);
    }

    @Transactional
    public Map<String, Object> replayCleanupPerformanceCheck(String requestId) {
        return paymentReplayService.replayCleanupPerformanceCheck(requestId);
    }

    @Transactional
    public Map<String, Object> replayTaskAutoHandle(boolean allowRequeueDead, int consumeMaxCount, int requeueMaxCount, String operator, String traceId) {
        return paymentReplayService.replayTaskAutoHandle(allowRequeueDead, consumeMaxCount, requeueMaxCount, operator, traceId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pageReplayAutoHandleIdempotencyRecords(int page, int size, String traceId, LocalDateTime startAt, LocalDateTime endAt) {
        return paymentReplayService.pageReplayAutoHandleIdempotencyRecords(page, size, traceId, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getReplayAutoHandleIdempotencyDetail(String traceId) {
        return paymentReplayService.getReplayAutoHandleIdempotencyDetail(traceId);
    }

    @Transactional
    public Map<String, Object> deleteReplayAutoHandleIdempotencyByTraceId(String traceId) {
        return paymentReplayService.deleteReplayAutoHandleIdempotencyByTraceId(traceId);
    }

    @Transactional
    public Map<String, Object> batchDeleteReplayAutoHandleIdempotencyBefore(LocalDateTime beforeTime) {
        return paymentReplayService.batchDeleteReplayAutoHandleIdempotencyBefore(beforeTime);
    }

    @Transactional
    public Map<String, Object> cleanupReplayAutoHandleIdempotencyRecords(int retainDays) {
        return paymentReplayService.cleanupReplayAutoHandleIdempotencyRecords(retainDays);
    }

    @Transactional
    public Map<String, Object> requeueReplayTask(Long taskId) {
        return paymentReplayService.requeueReplayTask(taskId);
    }

    @Transactional
    public Map<String, Object> batchRequeueDeadTasks(int maxCount) {
        return paymentReplayService.batchRequeueDeadTasks(maxCount);
    }

    @Transactional
    public Map<String, Object> replayPaymentCallback(Long callbackLogId) {
        return paymentReplayService.replayPaymentCallback(callbackLogId);
    }

    // ==================== 订单调度 ====================

    @Transactional
    public int autoCloseExpiredUnpaidOrders(int expireMinutes, int batchSize) {
        return resaleOrderService.autoCloseExpiredUnpaidOrders(expireMinutes, batchSize);
    }

    @Transactional
    public int autoConfirmDeliveredOrders(int confirmAfterMinutes, int batchSize, AuditContext auditContext) {
        return resaleOrderService.autoConfirmDeliveredOrders(confirmAfterMinutes, batchSize, auditContext);
    }

    // ==================== 审计日志 ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAuditLogs(String actionType, String targetId, int limit) {
        return auditLogService.listAuditLogs(actionType, targetId, limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pageAuditLogs(String actionType, String targetId, int page, int size) {
        return auditLogService.pageAuditLogs(actionType, targetId, page, size);
    }

    @Transactional(readOnly = true)
    public String exportAuditLogsCsv(String actionType, String targetId, int limit) {
        return auditLogService.exportAuditLogsCsv(actionType, targetId, limit);
    }

}
