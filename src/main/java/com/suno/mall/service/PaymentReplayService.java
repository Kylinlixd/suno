
package com.suno.mall.service;

import com.suno.mall.entity.OperationAuditLogEntity;
import com.suno.mall.entity.PaymentCallbackLogEntity;
import com.suno.mall.entity.PaymentReplayAutoHandleIdempotencyEntity;
import com.suno.mall.entity.PaymentReplayTaskEntity;
import com.suno.mall.dao.OperationAuditLogRepository;
import com.suno.mall.dao.PaymentCallbackLogRepository;
import com.suno.mall.dao.PaymentReplayAutoHandleIdempotencyRepository;
import com.suno.mall.dao.PaymentReplayTaskRepository;
import com.suno.mall.dao.ResaleOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 支付回调重放服务（含诊断、健康检查、幂等清理）
 */
@Service
public class PaymentReplayService {

    private static final String DIAGNOSIS_SCHEMA_VERSION = "1.0.0";
    private static final String CLEANUP_PERFORMANCE_CHECK_SCHEMA_VERSION = "1.0.0";
    private static final String HEALTH_SCHEMA_VERSION = "1.0.0";
    private static final String QUERY_AUDIT_ACTIONS_SCHEMA_VERSION = "1.0.0";
    private static final String STATUS_DICTIONARY_VERSION = "1.0.0";
    private static final String DEFAULT_LANG = "zh-CN";
    private static final String LANG_ZH_CN = "zh-CN";
    private static final String LANG_EN_US = "en-US";
    private static final String ACTION_PAYMENT_REPLAY_DIAGNOSIS_QUERY = "PAYMENT_REPLAY_DIAGNOSIS_QUERY";
    private static final String ACTION_PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY = "PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY";
    private static final String ACTION_PAYMENT_REPLAY_HEALTH_QUERY = "PAYMENT_REPLAY_HEALTH_QUERY";
    private static final String ACTION_PAYMENT_REPLAY_QUERY_AUDIT_ACTIONS_QUERY = "PAYMENT_REPLAY_QUERY_AUDIT_ACTIONS_QUERY";
    private static final String QUERY_AUDIT_DESC_KEY_HEALTH = "queryAudit.health";
    private static final String QUERY_AUDIT_DESC_KEY_DIAGNOSIS = "queryAudit.diagnosis";
    private static final String QUERY_AUDIT_DESC_KEY_CLEANUP_PERFORMANCE_CHECK = "queryAudit.cleanupPerformanceCheck";
    private static final String ACTION_ID_CONSUME_QUEUE = "ACTION_CONSUME_QUEUE";
    private static final String ACTION_ID_REQUEUE_DEAD = "ACTION_REQUEUE_DEAD";
    private static final String ACTION_ID_CHECK_FAILED_CALLBACK_LOGS = "ACTION_CHECK_FAILED_CALLBACK_LOGS";
    private static final String ACTION_ID_CHECK_CLEANUP_PERFORMANCE = "ACTION_CHECK_CLEANUP_PERFORMANCE";
    private static final String ACTION_ID_NO_ACTION = "ACTION_NO_ACTION";
    private static final String ACTION_CONSUME_QUEUE = "CONSUME_QUEUE";
    private static final String ACTION_REQUEUE_DEAD = "REQUEUE_DEAD";
    private static final String ACTION_CHECK_FAILED_CALLBACK_LOGS = "CHECK_FAILED_CALLBACK_LOGS";
    private static final String ACTION_CHECK_CLEANUP_PERFORMANCE = "CHECK_CLEANUP_PERFORMANCE";
    private static final String ACTION_NO_ACTION = "NO_ACTION";
    private static final String CATEGORY_QUEUE_BACKLOG = "QUEUE_BACKLOG";
    private static final String CATEGORY_DEAD_LETTER = "DEAD_LETTER";
    private static final String CATEGORY_FAILURE_ANALYSIS = "FAILURE_ANALYSIS";
    private static final String CATEGORY_CLEANUP_PERFORMANCE = "CLEANUP_PERFORMANCE";
    private static final String CATEGORY_NO_ACTION = "NO_ACTION";

    private static final String TARGET_ID_DIAGNOSIS = "DIAGNOSIS";
    private static final String TARGET_ID_CLEANUP_PERFORMANCE_CHECK = "CLEANUP_PERFORMANCE_CHECK";
    private static final String TARGET_ID_HEALTH = "HEALTH";
    private static final String TARGET_ID_QUERY_AUDIT_ACTIONS = "QUERY_AUDIT_ACTIONS";
    private static final String SUGGESTED_ACTION_KEY_ACTION_ID = "actionId";
    private static final String SUGGESTED_ACTION_KEY_ACTION = "action";
    private static final String SUGGESTED_ACTION_KEY_CATEGORY = "category";
    private static final String SUGGESTED_ACTION_KEY_PRIORITY = "priority";
    private static final String STATUS_OK = "OK";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_TEXT_OK = "队列健康且清理验收通过";
    private static final String STATUS_TEXT_WARN_DIAGNOSIS = "队列健康或清理验收存在告警";
    private static final String STATUS_TEXT_PASS = "清理性能验收通过";
    private static final String STATUS_TEXT_WARN_CLEANUP = "清理性能验收未通过";
    private static final String STATUS_TEXT_OK_HEALTH = "队列健康";
    private static final String STATUS_TEXT_WARN_HEALTH = "队列存在告警";
    private static final String SIGNAL_HAS_RECENT_CLEANUP_RUN = "HAS_RECENT_CLEANUP_RUN";
    private static final String SIGNAL_DURATION_RECOVERED = "DURATION_RECOVERED";
    private static final String SIGNAL_SLOW_WARN_CLEARED = "SLOW_WARN_CLEARED";
    private static final String QUERY_AUDIT_ACTION_STATUS_ACTIVE = "ACTIVE";
    private static final String QUERY_AUDIT_CONVENTION_STATUS_EXTERNAL_ONLY = "EXTERNAL_ONLY";

    @Value("${payment.callback.replay-max-retry:3}")
    private int replayMaxRetry;
    @Value("${payment.callback.replay-backoff-base-seconds:5}")
    private int replayBackoffBaseSeconds;
    @Value("${payment.callback.replay-backoff-max-seconds:300}")
    private int replayBackoffMaxSeconds;
    @Value("${payment.callback.replay-health-pending-threshold:100}")
    private int replayHealthPendingThreshold;
    @Value("${payment.callback.replay-health-dead-threshold:10}")
    private int replayHealthDeadThreshold;
    @Value("${payment.callback.replay-health-oldest-pending-minutes-threshold:30}")
    private int replayHealthOldestPendingMinutesThreshold;
    @Value("${payment.callback.replay-auto-handle-trace-idempotent-window-seconds:30}")
    private int replayAutoHandleTraceIdempotentWindowSeconds;
    @Value("${payment.callback.replay-auto-handle-idempotency-cleanup-warn-duration-ms:5000}")
    private long replayAutoHandleIdempotencyCleanupWarnDurationMs;
    @Value("${payment.callback.replay-health-cleanup-warn-lookback-minutes:30}")
    private int replayHealthCleanupWarnLookbackMinutes;

    private final ReentrantLock replayAutoHandleIdempotencyCleanupLock = new ReentrantLock();

    private final PaymentCallbackLogRepository paymentCallbackLogRepository;
    private final PaymentReplayTaskRepository paymentReplayTaskRepository;
    private final PaymentReplayAutoHandleIdempotencyRepository replayAutoHandleIdempotencyRepository;
    private final OperationAuditLogRepository operationAuditLogRepository;
    private final ResaleOrderRepository resaleOrderRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public PaymentReplayService(
            PaymentCallbackLogRepository paymentCallbackLogRepository,
            PaymentReplayTaskRepository paymentReplayTaskRepository,
            PaymentReplayAutoHandleIdempotencyRepository replayAutoHandleIdempotencyRepository,
            OperationAuditLogRepository operationAuditLogRepository,
            ResaleOrderRepository resaleOrderRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.paymentCallbackLogRepository = paymentCallbackLogRepository;
        this.paymentReplayTaskRepository = paymentReplayTaskRepository;
        this.replayAutoHandleIdempotencyRepository = replayAutoHandleIdempotencyRepository;
        this.operationAuditLogRepository = operationAuditLogRepository;
        this.resaleOrderRepository = resaleOrderRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    // ========== 支付回调日志 ==========

    @Transactional
    public void logPaymentCallback(String orderNo, String idempotencyKey, String payStatus,
            String nonce, long timestamp, String signature, String callbackStatus,
            String errorMessage, String responseBody, String source) {
        PaymentCallbackLogEntity log = new PaymentCallbackLogEntity();
        log.setOrderNo(orderNo);
        log.setIdempotencyKey(idempotencyKey);
        log.setPayStatus(payStatus);
        log.setNonce(nonce);
        log.setTimestamp(timestamp);
        log.setSignature(signature);
        log.setCallbackStatus(callbackStatus);
        log.setErrorMessage(errorMessage);
        log.setResponseBody(responseBody);
        log.setSource(source);
        log.setReplayCount(0);
        log.setCreatedAt(LocalDateTime.now());
        paymentCallbackLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pagePaymentCallbackLogs(int page, int size, String callbackStatus) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        Page<PaymentCallbackLogEntity> result;
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (callbackStatus != null && !callbackStatus.isBlank()) {
            result = paymentCallbackLogRepository.findByCallbackStatus(callbackStatus, pageable);
        } else {
            result = paymentCallbackLogRepository.findAll(pageable);
        }
        return Map.of("page", safePage, "size", safeSize,
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages(),
                "items", result.getContent().stream().map(log -> Map.<String, Object>ofEntries(
                        Map.entry("id", log.getId()), Map.entry("orderNo", log.getOrderNo()),
                        Map.entry("idempotencyKey", log.getIdempotencyKey()), Map.entry("payStatus", log.getPayStatus()),
                        Map.entry("callbackStatus", log.getCallbackStatus()),
                        Map.entry("errorMessage", log.getErrorMessage() == null ? "" : log.getErrorMessage()),
                        Map.entry("responseBody", log.getResponseBody()), Map.entry("source", log.getSource()),
                        Map.entry("replayCount", log.getReplayCount()), Map.entry("createdAt", log.getCreatedAt()),
                        Map.entry("lastReplayAt", log.getLastReplayAt())
                )).toList());
    }

    // ========== 重放任务 ==========

    @Transactional
    public Map<String, Object> enqueueReplayTask(Long callbackLogId) {
        PaymentCallbackLogEntity callbackLog = paymentCallbackLogRepository.findById(callbackLogId)
                .orElseThrow(() -> new IllegalArgumentException("回调日志不存在: " + callbackLogId));
        PaymentReplayTaskEntity existing = paymentReplayTaskRepository
                .findFirstByCallbackLogIdAndStatusInOrderByCreatedAtDesc(callbackLogId, List.of("PENDING", "PROCESSING"))
                .orElse(null);
        if (existing != null) {
            return Map.of("taskId", existing.getId(), "callbackLogId", existing.getCallbackLogId(),
                    "status", existing.getStatus(), "deduplicated", true);
        }
        PaymentReplayTaskEntity task = new PaymentReplayTaskEntity();
        task.setCallbackLogId(callbackLogId);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setLastError(null);
        task.setCreatedAt(LocalDateTime.now());
        task.setNextRetryAt(LocalDateTime.now());
        paymentReplayTaskRepository.save(task);
        return Map.of("taskId", task.getId(), "callbackLogId", callbackLog.getId(),
                "status", task.getStatus(), "deduplicated", false);
    }

    @Transactional
    public Map<String, Object> consumeReplayTasks(int maxCount) {
        int limit = Math.max(1, Math.min(200, maxCount));
        List<PaymentReplayTaskEntity> tasks = paymentReplayTaskRepository
                .findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc("PENDING", LocalDateTime.now(), PageRequest.of(0, limit));
        int success = 0, retriableFailed = 0, dead = 0, processed = 0;
        for (PaymentReplayTaskEntity task : tasks) {
            if (processed >= limit) break;
            processed++;
            try {
                task.setStatus("PROCESSING");
                task.setUpdatedAt(LocalDateTime.now());
                paymentReplayTaskRepository.save(task);
                replayPaymentCallback(task.getCallbackLogId());
                task.setStatus("SUCCESS");
                task.setUpdatedAt(LocalDateTime.now());
                paymentReplayTaskRepository.save(task);
                success++;
            } catch (RuntimeException ex) {
                int nextRetry = task.getRetryCount() + 1;
                task.setRetryCount(nextRetry);
                task.setLastError(ex.getMessage());
                task.setUpdatedAt(LocalDateTime.now());
                if (nextRetry >= replayMaxRetry) {
                    task.setStatus("DEAD");
                    task.setNextRetryAt(LocalDateTime.now());
                    dead++;
                } else {
                    task.setStatus("PENDING");
                    task.setNextRetryAt(LocalDateTime.now().plusSeconds(calcBackoffSeconds(nextRetry)));
                    retriableFailed++;
                }
                paymentReplayTaskRepository.save(task);
            }
        }
        return Map.of("processed", processed, "success", success, "retriableFailed", retriableFailed, "dead", dead);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pageReplayTasks(int page, int size, String status) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<PaymentReplayTaskEntity> result = (status != null && !status.isBlank())
                ? paymentReplayTaskRepository.findByStatus(status, pageable)
                : paymentReplayTaskRepository.findAll(pageable);
        return Map.of("page", safePage, "size", safeSize,
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages(),
                "items", result.getContent().stream().map(task -> Map.of(
                        "id", task.getId(), "callbackLogId", task.getCallbackLogId(),
                        "status", task.getStatus(), "retryCount", task.getRetryCount(),
                        "lastError", task.getLastError() == null ? "" : task.getLastError(),
                        "nextRetryAt", task.getNextRetryAt(), "createdAt", task.getCreatedAt(),
                        "updatedAt", task.getUpdatedAt()
                )).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> replayTaskSummary() {
        return Map.of("pending", paymentReplayTaskRepository.countByStatus("PENDING"),
                "processing", paymentReplayTaskRepository.countByStatus("PROCESSING"),
                "success", paymentReplayTaskRepository.countByStatus("SUCCESS"),
                "dead", paymentReplayTaskRepository.countByStatus("DEAD"),
                "readyToConsume", paymentReplayTaskRepository.countByStatusAndNextRetryAtBefore("PENDING", LocalDateTime.now()));
    }

    @Transactional
    public Map<String, Object> requeueReplayTask(Long taskId) {
        PaymentReplayTaskEntity task = paymentReplayTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("重放任务不存在: " + taskId));
        if (!"DEAD".equals(task.getStatus()) && !"FAILED".equals(task.getStatus())) {
            throw new IllegalArgumentException("仅 DEAD/FAILED 任务可重新投递");
        }
        PaymentReplayTaskEntity existing = paymentReplayTaskRepository
                .findFirstByCallbackLogIdAndStatusInOrderByCreatedAtDesc(task.getCallbackLogId(), List.of("PENDING", "PROCESSING"))
                .orElse(null);
        if (existing != null && !existing.getId().equals(task.getId())) {
            return Map.of("taskId", existing.getId(), "status", existing.getStatus(),
                    "retryCount", existing.getRetryCount(), "deduplicated", true);
        }
        task.setStatus("PENDING");
        task.setLastError(null);
        task.setUpdatedAt(LocalDateTime.now());
        task.setNextRetryAt(LocalDateTime.now());
        paymentReplayTaskRepository.save(task);
        return Map.of("taskId", task.getId(), "status", task.getStatus(),
                "retryCount", task.getRetryCount(), "deduplicated", false);
    }

    @Transactional
    public Map<String, Object> batchRequeueDeadTasks(int maxCount) {
        int limit = Math.max(1, Math.min(200, maxCount));
        List<PaymentReplayTaskEntity> deadTasks = paymentReplayTaskRepository.findByStatusOrderByCreatedAtAsc("DEAD", PageRequest.of(0, limit));
        int requeued = 0;
        for (PaymentReplayTaskEntity task : deadTasks) {
            task.setStatus("PENDING");
            task.setLastError(null);
            task.setUpdatedAt(LocalDateTime.now());
            task.setNextRetryAt(LocalDateTime.now());
            paymentReplayTaskRepository.save(task);
            requeued++;
        }
        return Map.of("requested", limit, "requeued", requeued);
    }

    @Transactional
    public Map<String, Object> replayPaymentCallback(Long callbackLogId) {
        PaymentCallbackLogEntity callbackLog = paymentCallbackLogRepository.findById(callbackLogId)
                .orElseThrow(() -> new IllegalArgumentException("回调日志不存在: " + callbackLogId));
        try {
            // 委托给 ResaleOrderService 的幂等支付方法 - 通过 ResaleOrderRepository 直接操作
            var order = resaleOrderRepository.findByOrderNo(callbackLog.getOrderNo())
                    .orElseThrow(() -> new IllegalArgumentException("二销订单不存在: " + callbackLog.getOrderNo()));
            if (!"UNPAID".equals(order.getPayStatus())) {
                // 已支付，幂等返回
            } else {
                order.setPayStatus("PAID");
                order.setFulfillStatus("TO_DELIVER");
                resaleOrderRepository.save(order);
                auditLogService.logAction("RESALE_ORDER_PAY", "RESALE_ORDER", callbackLog.getOrderNo(), "payStatus=PAID,replay=true");
            }
            callbackLog.setReplayCount(callbackLog.getReplayCount() + 1);
            callbackLog.setLastReplayAt(LocalDateTime.now());
            callbackLog.setCallbackStatus("REPLAY_SUCCESS");
            callbackLog.setErrorMessage(null);
            paymentCallbackLogRepository.save(callbackLog);
            return Map.of("callbackLogId", callbackLogId, "replayStatus", "SUCCESS");
        } catch (RuntimeException ex) {
            callbackLog.setReplayCount(callbackLog.getReplayCount() + 1);
            callbackLog.setLastReplayAt(LocalDateTime.now());
            callbackLog.setCallbackStatus("REPLAY_FAILED");
            callbackLog.setErrorMessage(ex.getMessage());
            paymentCallbackLogRepository.save(callbackLog);
            throw ex;
        }
    }

    // ========== 健康检查 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> replayTaskHealth(String requestId) {
        return replayTaskHealth(requestId, true);
    }

    private Map<String, Object> replayTaskHealth(String requestId, boolean writeAuditLog) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedRequestId = resolveRequestId(requestId);
        long pending = paymentReplayTaskRepository.countByStatus("PENDING");
        long processing = paymentReplayTaskRepository.countByStatus("PROCESSING");
        long success = paymentReplayTaskRepository.countByStatus("SUCCESS");
        long dead = paymentReplayTaskRepository.countByStatus("DEAD");
        long readyToConsume = paymentReplayTaskRepository.countByStatusAndNextRetryAtBefore("PENDING", now);
        LocalDateTime oldestPendingCreatedAt = paymentReplayTaskRepository.findFirstByStatusOrderByCreatedAtAsc("PENDING")
                .map(PaymentReplayTaskEntity::getCreatedAt).orElse(null);
        long oldestPendingAgeMinutes = oldestPendingCreatedAt == null ? 0 : ChronoUnit.MINUTES.between(oldestPendingCreatedAt, now);

        List<String> alerts = new ArrayList<>();
        if (pending >= replayHealthPendingThreshold) alerts.add("PENDING积压超过阈值: pending=" + pending + ", threshold=" + replayHealthPendingThreshold);
        if (dead >= replayHealthDeadThreshold) alerts.add("DEAD任务超过阈值: dead=" + dead + ", threshold=" + replayHealthDeadThreshold);
        if (oldestPendingAgeMinutes >= replayHealthOldestPendingMinutesThreshold) alerts.add("最老PENDING任务等待超时: ageMinutes=" + oldestPendingAgeMinutes + ", threshold=" + replayHealthOldestPendingMinutesThreshold);
        OperationAuditLogEntity latestCleanupWarnLog = operationAuditLogRepository.findByActionType(
                "PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP_WARN", PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().findFirst().orElse(null);
        boolean recentCleanupSlowWarn = latestCleanupWarnLog != null && !latestCleanupWarnLog.getCreatedAt().isBefore(now.minusMinutes(Math.max(1, replayHealthCleanupWarnLookbackMinutes)));
        if (recentCleanupSlowWarn) alerts.add("幂等清理近期出现慢任务: latestWarnAt=" + latestCleanupWarnLog.getCreatedAt());

        Map<String, Object> response = Map.ofEntries(
                Map.entry("healthSchemaVersion", HEALTH_SCHEMA_VERSION),
                Map.entry("requestId", normalizedRequestId), Map.entry("generatedAt", now),
                Map.entry("statusDictionaryVersion", STATUS_DICTIONARY_VERSION),
                Map.entry("statusDictionary", healthStatusDictionary()),
                Map.entry("overallStatus", alerts.isEmpty() ? STATUS_OK : STATUS_WARN),
                Map.entry("alerts", alerts),
                Map.entry("metrics", Map.of("pending", pending, "processing", processing, "success", success, "dead", dead,
                        "readyToConsume", readyToConsume, "oldestPendingCreatedAt", oldestPendingCreatedAt == null ? "" : oldestPendingCreatedAt,
                        "oldestPendingAgeMinutes", oldestPendingAgeMinutes, "recentCleanupSlowWarn", recentCleanupSlowWarn,
                        "latestCleanupWarnAt", latestCleanupWarnLog == null ? "" : latestCleanupWarnLog.getCreatedAt())),
                Map.entry("thresholds", Map.of("pending", replayHealthPendingThreshold, "dead", replayHealthDeadThreshold,
                        "oldestPendingAgeMinutes", replayHealthOldestPendingMinutesThreshold,
                        "cleanupWarnLookbackMinutes", Math.max(1, replayHealthCleanupWarnLookbackMinutes)))
        );
        if (writeAuditLog) {
            auditLogService.logExternalQueryAction(ACTION_PAYMENT_REPLAY_HEALTH_QUERY, TARGET_ID_HEALTH,
                    "requestId=" + normalizedRequestId + ",overallStatus=" + response.get("overallStatus") + ",alertCount=" + alerts.size());
        }
        return response;
    }

    // ========== 诊断 ==========

    @Transactional
    public Map<String, Object> replayTaskDiagnosis(String requestId) { return replayTaskDiagnosis(requestId, true); }

    private Map<String, Object> replayTaskDiagnosis(String requestId, boolean writeAuditLog) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> health = replayTaskHealth(requestId, false);
        String normalizedRequestId = resolveRequestId(requestId);
        Map<String, Object> cleanupPerformanceCheck = replayCleanupPerformanceCheck(normalizedRequestId, false);
        String healthOverallStatus = String.valueOf(health.get("overallStatus"));
        String cleanupOverallStatus = String.valueOf(cleanupPerformanceCheck.get("overallStatus"));
        String diagnosisOverallStatus = (STATUS_OK.equals(healthOverallStatus) && STATUS_PASS.equals(cleanupOverallStatus)) ? STATUS_OK : STATUS_WARN;
        @SuppressWarnings("unchecked") Map<String, Object> metrics = (Map<String, Object>) health.get("metrics");
        long pending = ((Number) metrics.get("pending")).longValue();
        long dead = ((Number) metrics.get("dead")).longValue();
        long readyToConsume = ((Number) metrics.get("readyToConsume")).longValue();
        long oldestPendingAgeMinutes = ((Number) metrics.get("oldestPendingAgeMinutes")).longValue();
        boolean recentCleanupSlowWarn = Boolean.TRUE.equals(metrics.get("recentCleanupSlowWarn"));

        List<Map<String, Object>> suggestedActions = new ArrayList<>();
        if (readyToConsume > 0) suggestedActions.add(buildSuggestedAction(ACTION_ID_CONSUME_QUEUE, ACTION_CONSUME_QUEUE, CATEGORY_QUEUE_BACKLOG, 1, Map.of("api", "POST /api/admin/payment/callback-logs/replay/consume", "suggestedBody", Map.of("maxCount", Math.min(200L, readyToConsume)), "reason", "当前存在可立即消费任务")));
        if (dead > 0) suggestedActions.add(buildSuggestedAction(ACTION_ID_REQUEUE_DEAD, ACTION_REQUEUE_DEAD, CATEGORY_DEAD_LETTER, 2, Map.of("api", "POST /api/admin/payment/replay-tasks/requeue/dead", "suggestedBody", Map.of("maxCount", Math.min(200L, dead)), "reason", "存在死信任务")));
        if (oldestPendingAgeMinutes >= replayHealthOldestPendingMinutesThreshold || pending >= replayHealthPendingThreshold) suggestedActions.add(buildSuggestedAction(ACTION_ID_CHECK_FAILED_CALLBACK_LOGS, ACTION_CHECK_FAILED_CALLBACK_LOGS, CATEGORY_FAILURE_ANALYSIS, 3, Map.of("api", "GET /api/admin/payment/callback-logs?callbackStatus=FAILED&page=0&size=20", "reason", "存在持续积压或等待超时")));
        if (recentCleanupSlowWarn) suggestedActions.add(buildSuggestedAction(ACTION_ID_CHECK_CLEANUP_PERFORMANCE, ACTION_CHECK_CLEANUP_PERFORMANCE, CATEGORY_CLEANUP_PERFORMANCE, STATUS_WARN.equals(cleanupOverallStatus) ? 2 : 4, Map.of("api", "POST /api/admin/payment/replay-tasks/auto-handle-idempotency/cleanup", "suggestedBody", Map.of("retainDays", 3), "reason", "近期出现幂等清理慢任务")));
        if (suggestedActions.isEmpty()) suggestedActions.add(buildSuggestedAction(ACTION_ID_NO_ACTION, ACTION_NO_ACTION, CATEGORY_NO_ACTION, 1, Map.of("reason", "队列状态健康，无需人工干预")));
        else suggestedActions = new ArrayList<>(suggestedActions.stream().sorted((a, b) -> Integer.compare(((Number) a.get(SUGGESTED_ACTION_KEY_PRIORITY)).intValue(), ((Number) b.get(SUGGESTED_ACTION_KEY_PRIORITY)).intValue())).toList());

        Map<String, Object> response = Map.ofEntries(
                Map.entry("diagnosisSchemaVersion", DIAGNOSIS_SCHEMA_VERSION), Map.entry("requestId", normalizedRequestId),
                Map.entry("generatedAt", now), Map.entry("overallStatus", diagnosisOverallStatus),
                Map.entry("statusBreakdown", Map.of("queueHealth", healthOverallStatus, "cleanupPerformanceCheck", cleanupOverallStatus)),
                Map.entry("alerts", health.get("alerts")), Map.entry("metrics", metrics),
                Map.entry("thresholds", health.get("thresholds")), Map.entry("suggestedActions", suggestedActions),
                Map.entry("cleanupPerformanceCheck", cleanupPerformanceCheck),
                Map.entry("dependentSchemaVersions", Map.of("healthSchemaVersion", health.get("healthSchemaVersion"), "cleanupPerformanceCheckSchemaVersion", cleanupPerformanceCheck.get("cleanupPerformanceCheckSchemaVersion"))),
                Map.entry("statusDictionaryVersion", STATUS_DICTIONARY_VERSION), Map.entry("statusDictionary", diagnosisStatusDictionary())
        );
        if (writeAuditLog) auditLogService.logExternalQueryAction(ACTION_PAYMENT_REPLAY_DIAGNOSIS_QUERY, TARGET_ID_DIAGNOSIS, "requestId=" + normalizedRequestId + ",overallStatus=" + diagnosisOverallStatus);
        return response;
    }

    // ========== 清理性能验收 ==========

    @Transactional
    public Map<String, Object> replayCleanupPerformanceCheck(String requestId) { return replayCleanupPerformanceCheck(requestId, true); }

    private Map<String, Object> replayCleanupPerformanceCheck(String requestId, boolean writeAuditLog) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedRequestId = resolveRequestId(requestId);
        int safeLookbackMinutes = Math.max(1, replayHealthCleanupWarnLookbackMinutes);
        OperationAuditLogEntity latestCleanupLog = operationAuditLogRepository.findByActionType("PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP", PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))).stream().findFirst().orElse(null);
        OperationAuditLogEntity latestCleanupWarnLog = operationAuditLogRepository.findByActionType("PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP_WARN", PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))).stream().findFirst().orElse(null);
        boolean hasRecentCleanupRun = latestCleanupLog != null && !latestCleanupLog.getCreatedAt().isBefore(now.minusMinutes(safeLookbackMinutes));
        boolean recentCleanupSlowWarn = latestCleanupWarnLog != null && !latestCleanupWarnLog.getCreatedAt().isBefore(now.minusMinutes(safeLookbackMinutes));
        Long latestCleanupDurationMs = latestCleanupLog == null ? null : extractLongFromAuditDetail(latestCleanupLog.getDetail(), "durationMs");
        boolean durationRecovered = latestCleanupDurationMs != null && latestCleanupDurationMs <= replayAutoHandleIdempotencyCleanupWarnDurationMs;
        boolean pass = hasRecentCleanupRun && durationRecovered && !recentCleanupSlowWarn;

        List<Map<String, Object>> signals = List.of(
                Map.of("name", SIGNAL_HAS_RECENT_CLEANUP_RUN, "pass", hasRecentCleanupRun, "expected", "观察窗口内至少有一次 cleanup 执行"),
                Map.of("name", SIGNAL_DURATION_RECOVERED, "pass", durationRecovered, "expected", "latest durationMs <= warnThresholdMs"),
                Map.of("name", SIGNAL_SLOW_WARN_CLEARED, "pass", !recentCleanupSlowWarn, "expected", "观察窗口内无 CLEANUP_WARN")
        );
        List<String> recommendations = new ArrayList<>();
        if (!hasRecentCleanupRun) recommendations.add("先手工触发一次 cleanup");
        if (!durationRecovered) recommendations.add("缩小 retainDays 后复跑 cleanup");
        if (recentCleanupSlowWarn) recommendations.add("排查数据库慢 SQL");
        if (recommendations.isEmpty()) recommendations.add("清理性能验收通过");

        Map<String, Object> response = Map.ofEntries(
                Map.entry("cleanupPerformanceCheckSchemaVersion", CLEANUP_PERFORMANCE_CHECK_SCHEMA_VERSION),
                Map.entry("requestId", normalizedRequestId), Map.entry("generatedAt", now),
                Map.entry("statusDictionaryVersion", STATUS_DICTIONARY_VERSION), Map.entry("statusDictionary", cleanupStatusDictionary()),
                Map.entry("overallStatus", pass ? STATUS_PASS : STATUS_WARN),
                Map.entry("signals", signals), Map.entry("recommendations", recommendations),
                Map.entry("metrics", Map.of("recentCleanupSlowWarn", recentCleanupSlowWarn, "latestCleanupDurationMs", latestCleanupDurationMs == null ? "" : latestCleanupDurationMs)),
                Map.entry("thresholds", Map.of("cleanupWarnDurationMs", replayAutoHandleIdempotencyCleanupWarnDurationMs, "cleanupWarnLookbackMinutes", safeLookbackMinutes))
        );
        if (writeAuditLog) auditLogService.logExternalQueryAction(ACTION_PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY, TARGET_ID_CLEANUP_PERFORMANCE_CHECK, "requestId=" + normalizedRequestId + ",overallStatus=" + (pass ? STATUS_PASS : STATUS_WARN));
        return response;
    }

    // ========== 查询审计动作 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> replayQueryAuditActions(String requestId, String lang) {
        String normalizedRequestId = resolveRequestId(requestId);
        String normalizedLang = normalizeLang(lang);
        Map<String, String> descDictionary = queryAuditActionsDescDictionary(normalizedLang);
        List<Map<String, Object>> actions = buildQueryAuditActions(descDictionary);
        Map<String, Object> response = Map.ofEntries(
                Map.entry("queryAuditActionsSchemaVersion", QUERY_AUDIT_ACTIONS_SCHEMA_VERSION),
                Map.entry("requestId", normalizedRequestId), Map.entry("generatedAt", LocalDateTime.now()),
                Map.entry("lang", normalizedLang), Map.entry("descDictionary", descDictionary),
                Map.entry("actions", actions), Map.entry("convention", queryAuditActionsConventionText(normalizedLang)),
                Map.entry("conventionStatus", QUERY_AUDIT_CONVENTION_STATUS_EXTERNAL_ONLY)
        );
        auditLogService.logExternalQueryAction(ACTION_PAYMENT_REPLAY_QUERY_AUDIT_ACTIONS_QUERY, TARGET_ID_QUERY_AUDIT_ACTIONS, "requestId=" + normalizedRequestId);
        return response;
    }

    // ========== 自动处理 ==========

    @Transactional
    public Map<String, Object> replayTaskAutoHandle(boolean allowRequeueDead, int consumeMaxCount, int requeueMaxCount, String operator, String traceId) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedOperator = (operator == null || operator.isBlank()) ? "anonymous" : operator.trim();
        String normalizedTraceId = (traceId == null || traceId.isBlank()) ? "" : traceId.trim();

        if (!normalizedTraceId.isBlank()) {
            replayAutoHandleIdempotencyRepository.deleteByExpireAtBefore(now);
            PaymentReplayAutoHandleIdempotencyEntity cached = replayAutoHandleIdempotencyRepository.findByTraceId(normalizedTraceId).orElse(null);
            if (cached != null && !cached.getExpireAt().isBefore(now)) {
                Map<String, Object> cachedResponse = fromJsonMap(cached.getResponseJson());
                cachedResponse.put("idempotentReplay", true);
                return cachedResponse;
            }
        }

        int safeConsumeMaxCount = Math.max(1, Math.min(200, consumeMaxCount));
        int safeRequeueMaxCount = Math.max(1, Math.min(200, requeueMaxCount));
        Map<String, Object> diagnosis = replayTaskDiagnosis(normalizedTraceId, false);
        @SuppressWarnings("unchecked") Map<String, Object> metrics = (Map<String, Object>) diagnosis.get("metrics");
        long readyToConsume = ((Number) metrics.get("readyToConsume")).longValue();
        long dead = ((Number) metrics.get("dead")).longValue();

        Map<String, Object> consumeResult = readyToConsume > 0 ? consumeReplayTasks((int) Math.min(readyToConsume, safeConsumeMaxCount)) : Map.of("processed", 0, "success", 0, "skipped", true, "reason", "无可立即消费任务");
        Map<String, Object> requeueResult = (allowRequeueDead && dead > 0) ? batchRequeueDeadTasks((int) Math.min(dead, safeRequeueMaxCount)) : Map.of("requested", 0, "requeued", 0, "skipped", true);

        Map<String, Object> afterHealth = replayTaskHealth(normalizedTraceId, false);
        auditLogService.logAction("PAYMENT_REPLAY_AUTO_HANDLE", "PAYMENT_REPLAY_QUEUE", "AUTO_HANDLE",
                "operator=" + normalizedOperator + ",traceId=" + (normalizedTraceId.isBlank() ? "N/A" : normalizedTraceId));

        Map<String, Object> response = new HashMap<>();
        response.put("beforeDiagnosis", diagnosis);
        response.put("executedActions", Map.of("consume", consumeResult, "requeueDead", requeueResult));
        response.put("afterHealth", afterHealth);
        response.put("idempotentReplay", false);

        if (!normalizedTraceId.isBlank()) saveAutoHandleIdempotentRecord(normalizedTraceId, response, now);
        return response;
    }

    // ========== 幂等记录管理 ==========

    @Transactional(readOnly = true)
    public Map<String, Object> pageReplayAutoHandleIdempotencyRecords(int page, int size, String traceId, LocalDateTime startAt, LocalDateTime endAt) {
        int safePage = Math.max(0, page), safeSize = Math.max(1, Math.min(200, size));
        Page<PaymentReplayAutoHandleIdempotencyEntity> result = replayAutoHandleIdempotencyRepository.findAll(
                buildReplayAutoHandleIdempotencySpec(traceId, startAt, endAt), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return Map.of("page", safePage, "size", safeSize, "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages(),
                "items", result.getContent().stream().map(item -> Map.of("id", item.getId(), "traceId", item.getTraceId(), "createdAt", item.getCreatedAt(), "expireAt", item.getExpireAt())).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getReplayAutoHandleIdempotencyDetail(String traceId) {
        if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId 不能为空");
        PaymentReplayAutoHandleIdempotencyEntity record = replayAutoHandleIdempotencyRepository.findByTraceId(traceId.trim()).orElseThrow(() -> new IllegalArgumentException("幂等记录不存在: " + traceId));
        return Map.of("id", record.getId(), "traceId", record.getTraceId(), "createdAt", record.getCreatedAt(), "expireAt", record.getExpireAt(), "expired", record.getExpireAt().isBefore(LocalDateTime.now()), "response", fromJsonMap(record.getResponseJson()));
    }

    @Transactional
    public Map<String, Object> deleteReplayAutoHandleIdempotencyByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId 不能为空");
        return Map.of("traceId", traceId.trim(), "deleted", replayAutoHandleIdempotencyRepository.deleteByTraceId(traceId.trim()));
    }

    @Transactional
    public Map<String, Object> batchDeleteReplayAutoHandleIdempotencyBefore(LocalDateTime beforeTime) {
        if (beforeTime == null) throw new IllegalArgumentException("beforeTime 不能为空");
        return Map.of("beforeTime", beforeTime, "deleted", replayAutoHandleIdempotencyRepository.deleteByCreatedAtBefore(beforeTime));
    }

    @Transactional
    public Map<String, Object> cleanupReplayAutoHandleIdempotencyRecords(int retainDays) {
        if (!replayAutoHandleIdempotencyCleanupLock.tryLock()) return Map.of("skipped", true, "reason", "清理任务正在执行中", "durationMs", 0);
        try {
            long startMs = System.currentTimeMillis();
            int safeRetainDays = Math.max(1, retainDays);
            LocalDateTime now = LocalDateTime.now();
            long expiredDeleted = replayAutoHandleIdempotencyRepository.deleteByExpireAtBefore(now);
            long historyDeleted = replayAutoHandleIdempotencyRepository.deleteByCreatedAtBefore(now.minusDays(safeRetainDays));
            long durationMs = System.currentTimeMillis() - startMs;
            auditLogService.logAction("PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP", "PAYMENT_REPLAY_QUEUE", "AUTO_HANDLE_IDEMPOTENCY", "retainDays=" + safeRetainDays + ",totalDeleted=" + (expiredDeleted + historyDeleted) + ",durationMs=" + durationMs);
            if (durationMs > replayAutoHandleIdempotencyCleanupWarnDurationMs) auditLogService.logAction("PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP_WARN", "PAYMENT_REPLAY_QUEUE", "AUTO_HANDLE_IDEMPOTENCY", "durationMs=" + durationMs);
            return Map.of("retainDays", safeRetainDays, "expiredDeleted", expiredDeleted, "historyDeleted", historyDeleted, "totalDeleted", expiredDeleted + historyDeleted, "skipped", false, "durationMs", durationMs, "slowWarnTriggered", durationMs > replayAutoHandleIdempotencyCleanupWarnDurationMs);
        } finally { replayAutoHandleIdempotencyCleanupLock.unlock(); }
    }

    // ========== 私有辅助方法 ==========

    private long calcBackoffSeconds(int retryCount) { return Math.min((long) replayBackoffBaseSeconds << Math.max(0, retryCount - 1), replayBackoffMaxSeconds); }
    private String resolveRequestId(String requestId) { String n = (requestId == null || requestId.isBlank()) ? "" : requestId.trim(); return n.isBlank() ? "req-" + UUID.randomUUID().toString().replace("-", "") : n; }
    private String normalizeLang(String lang) { if (lang == null || lang.isBlank()) return DEFAULT_LANG; String n = lang.trim(); return LANG_EN_US.equalsIgnoreCase(n) ? LANG_EN_US : LANG_ZH_CN; }

    private Map<String, Object> buildSuggestedAction(String actionId, String action, String category, int priority, Map<String, Object> extras) {
        Map<String, Object> payload = new HashMap<>(Map.of(SUGGESTED_ACTION_KEY_ACTION_ID, actionId, SUGGESTED_ACTION_KEY_ACTION, action, SUGGESTED_ACTION_KEY_CATEGORY, category, SUGGESTED_ACTION_KEY_PRIORITY, priority));
        payload.putAll(extras);
        return payload;
    }

    private Map<String, String> diagnosisStatusDictionary() { return Map.of(STATUS_OK, STATUS_TEXT_OK, STATUS_WARN, STATUS_TEXT_WARN_DIAGNOSIS); }
    private Map<String, String> healthStatusDictionary() { return Map.of(STATUS_OK, STATUS_TEXT_OK_HEALTH, STATUS_WARN, STATUS_TEXT_WARN_HEALTH); }
    private Map<String, String> cleanupStatusDictionary() { return Map.of(STATUS_PASS, STATUS_TEXT_PASS, STATUS_WARN, STATUS_TEXT_WARN_CLEANUP); }

    private List<Map<String, Object>> buildQueryAuditActions(Map<String, String> descDictionary) {
        return List.of(Map.of("actionType", ACTION_PAYMENT_REPLAY_HEALTH_QUERY, "descKey", QUERY_AUDIT_DESC_KEY_HEALTH, "description", descDictionary.get(QUERY_AUDIT_DESC_KEY_HEALTH), "status", QUERY_AUDIT_ACTION_STATUS_ACTIVE),
                Map.of("actionType", ACTION_PAYMENT_REPLAY_DIAGNOSIS_QUERY, "descKey", QUERY_AUDIT_DESC_KEY_DIAGNOSIS, "description", descDictionary.get(QUERY_AUDIT_DESC_KEY_DIAGNOSIS), "status", QUERY_AUDIT_ACTION_STATUS_ACTIVE),
                Map.of("actionType", ACTION_PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY, "descKey", QUERY_AUDIT_DESC_KEY_CLEANUP_PERFORMANCE_CHECK, "description", descDictionary.get(QUERY_AUDIT_DESC_KEY_CLEANUP_PERFORMANCE_CHECK), "status", QUERY_AUDIT_ACTION_STATUS_ACTIVE));
    }

    private Map<String, String> queryAuditActionsDescDictionary(String lang) {
        return LANG_EN_US.equals(lang) ? Map.of(QUERY_AUDIT_DESC_KEY_HEALTH, "Health query", QUERY_AUDIT_DESC_KEY_DIAGNOSIS, "Diagnosis query", QUERY_AUDIT_DESC_KEY_CLEANUP_PERFORMANCE_CHECK, "Cleanup performance check query")
                : Map.of(QUERY_AUDIT_DESC_KEY_HEALTH, "健康查询", QUERY_AUDIT_DESC_KEY_DIAGNOSIS, "诊断查询", QUERY_AUDIT_DESC_KEY_CLEANUP_PERFORMANCE_CHECK, "清理性能验收查询");
    }

    private String queryAuditActionsConventionText(String lang) { return LANG_EN_US.equals(lang) ? "*_QUERY actions are logged only for externally triggered query requests" : "*_QUERY 仅记录外部请求触发的查询"; }

    private void saveAutoHandleIdempotentRecord(String traceId, Map<String, Object> response, LocalDateTime now) {
        PaymentReplayAutoHandleIdempotencyEntity record = new PaymentReplayAutoHandleIdempotencyEntity();
        record.setTraceId(traceId); record.setResponseJson(toJson(response)); record.setCreatedAt(now);
        record.setExpireAt(now.plusSeconds(Math.max(1, replayAutoHandleTraceIdempotentWindowSeconds)));
        try { replayAutoHandleIdempotencyRepository.save(record); } catch (DataIntegrityViolationException ex) { /* 并发唯一键冲突 */ }
    }

    private org.springframework.data.jpa.domain.Specification<PaymentReplayAutoHandleIdempotencyEntity> buildReplayAutoHandleIdempotencySpec(String traceId, LocalDateTime startAt, LocalDateTime endAt) {
        return (root, query, cb) -> { List<Predicate> predicates = new ArrayList<>(); if (traceId != null && !traceId.isBlank()) predicates.add(cb.like(root.get("traceId"), "%" + traceId.trim() + "%")); if (startAt != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startAt)); if (endAt != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endAt)); return cb.and(predicates.toArray(new Predicate[0])); };
    }

    private Long extractLongFromAuditDetail(String detail, String key) { if (detail == null || detail.isBlank()) return null; for (String pair : detail.split(",")) { String[] kv = pair.split("=", 2); if (kv.length == 2 && key.equals(kv[0].trim())) try { return Long.parseLong(kv[1].trim()); } catch (NumberFormatException ignored) { return null; } } return null; }
    private String toJson(Map<String, Object> value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalStateException("序列化失败", ex); } }
    private Map<String, Object> fromJsonMap(String json) { try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); } catch (JsonProcessingException ex) { throw new IllegalStateException("反序列化失败", ex); } }
}
