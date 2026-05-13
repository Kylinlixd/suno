package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.entity.AuthExportTaskEntity;
import com.suno.mall.entity.OperationAuditLogEntity;
import com.suno.mall.dao.AuthExportTaskRepository;
import com.suno.mall.dao.OperationAuditLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 安全事件服务
 * <p>
 * 负责安全事件汇总/时间线/风险用户TopN、异步导出任务管理。
 */
@Service
public class SecurityEventService {

    private static final String TARGET_TYPE_AUTH_EXPORT_TASK = "AUTH_EXPORT_TASK";
    private static final String ACTION_EXPORT_TASK_CREATED = "AUTH_EXPORT_TASK_CREATED";
    private static final String ACTION_EXPORT_TASK_SUCCESS = "AUTH_EXPORT_TASK_SUCCESS";
    private static final String ACTION_EXPORT_TASK_FAILED = "AUTH_EXPORT_TASK_FAILED";
    private static final String ACTION_EXPORT_TASK_RETRY = "AUTH_EXPORT_TASK_RETRY";
    private static final String ACTION_EXPORT_TASK_TIMEOUT = "AUTH_EXPORT_TASK_TIMEOUT";
    private static final String ACTION_REFRESH_REPLAY_BLOCKED = "AUTH_REFRESH_REPLAY_BLOCKED";
    private static final String ACTION_ADMIN_SESSION_REVOKE_DEVICE = "AUTH_ADMIN_SESSION_REVOKE_DEVICE";
    private static final String ACTION_ADMIN_SESSION_REVOKE_ALL = "AUTH_ADMIN_SESSION_REVOKE_ALL";
    private static final String ACTION_SESSION_REVOKE_DEVICE = "AUTH_SESSION_REVOKE_DEVICE";
    private static final String ACTION_SESSION_REVOKE_ALL = "AUTH_SESSION_REVOKE_ALL";
    private static final String ACTION_LOGOUT = "AUTH_LOGOUT";
    private static final String ACTION_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    private static final String ACTION_REFRESH_SUCCESS = "AUTH_REFRESH_SUCCESS";
    private static final String ERROR_CODE_NONE = "NONE";
    private static final String ERROR_CODE_EXECUTION_FAILED = "EXPORT_EXECUTION_FAILED";
    private static final String ERROR_CODE_TASK_TIMEOUT = "EXPORT_TASK_TIMEOUT";

    static final List<String> SECURITY_EVENT_ACTIONS = List.of(
            ACTION_REFRESH_REPLAY_BLOCKED,
            ACTION_ADMIN_SESSION_REVOKE_DEVICE,
            ACTION_ADMIN_SESSION_REVOKE_ALL,
            ACTION_SESSION_REVOKE_DEVICE,
            ACTION_SESSION_REVOKE_ALL,
            ACTION_EXPORT_TASK_TIMEOUT,
            ACTION_EXPORT_TASK_FAILED,
            ACTION_EXPORT_TASK_RETRY,
            ACTION_EXPORT_TASK_SUCCESS,
            ACTION_EXPORT_TASK_CREATED,
            ACTION_LOGOUT,
            ACTION_LOGIN_SUCCESS,
            ACTION_REFRESH_SUCCESS
    );

    private final AuthExportTaskRepository authExportTaskRepository;
    private final OperationAuditLogRepository operationAuditLogRepository;

    @Value("${security.auth.export-task.retain-days:7}")
    private int exportTaskRetainDays;

    @Value("${security.auth.export-task.max-running:3}")
    private int exportTaskMaxRunning;

    @Value("${security.auth.export-task.default-max-retry:2}")
    private int exportTaskDefaultMaxRetry;

    @Value("${security.auth.export-task.running-timeout-minutes:10}")
    private int exportTaskRunningTimeoutMinutes;

    public SecurityEventService(
            AuthExportTaskRepository authExportTaskRepository,
            OperationAuditLogRepository operationAuditLogRepository
    ) {
        this.authExportTaskRepository = authExportTaskRepository;
        this.operationAuditLogRepository = operationAuditLogRepository;
    }

    // ==================== 安全事件查询 ====================

    @Transactional(readOnly = true)
    public Map<String, Object> adminSecurityEventsSummary(int lookbackMinutes) {
        int safeLookback = lookbackMinutes <= 0 ? 60 : lookbackMinutes;
        LocalDateTime from = LocalDateTime.now().minusMinutes(safeLookback);

        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (String action : SECURITY_EVENT_ACTIONS) {
            int count = (int) operationAuditLogRepository.findByActionTypeOrderByCreatedAtDesc(action)
                    .stream()
                    .filter(log -> !log.getCreatedAt().isBefore(from))
                    .count();
            counts.put(action, count);
            total += count;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("lookbackMinutes", safeLookback);
        data.put("from", from.toString());
        data.put("totalEvents", total);
        data.put("counts", counts);
        data.put("recommendation", buildSecurityRecommendation(counts));
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSecurityEventsTimeline(int lookbackMinutes, List<String> actionTypes) {
        int safeLookback = lookbackMinutes <= 0 ? 60 : lookbackMinutes;
        List<String> selectedActions = normalizeActionTypes(actionTypes);
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime from = now.minusMinutes(safeLookback - 1L);

        List<Map<String, Object>> points = new ArrayList<>();
        LocalDateTime cursor = from;
        while (!cursor.isAfter(now)) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("minute", cursor.toString());
            point.put("total", 0);
            Map<String, Integer> actionCounts = new LinkedHashMap<>();
            for (String action : selectedActions) {
                actionCounts.put(action, 0);
            }
            point.put("counts", actionCounts);
            points.add(point);
            cursor = cursor.plusMinutes(1);
        }

        for (String action : selectedActions) {
            List<OperationAuditLogEntity> logs = operationAuditLogRepository.findByActionTypeOrderByCreatedAtDesc(action);
            for (OperationAuditLogEntity log : logs) {
                LocalDateTime created = log.getCreatedAt().withSecond(0).withNano(0);
                if (created.isBefore(from)) break;
                if (created.isAfter(now)) continue;
                int index = (int) java.time.Duration.between(from, created).toMinutes();
                if (index >= 0 && index < points.size()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> counts = (Map<String, Integer>) points.get(index).get("counts");
                    counts.put(action, counts.get(action) + 1);
                    points.get(index).put("total", (int) points.get(index).get("total") + 1);
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("lookbackMinutes", safeLookback);
        data.put("from", from.toString());
        data.put("to", now.toString());
        data.put("actions", selectedActions);
        data.put("points", points);
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSecurityRiskUsersTop(int lookbackMinutes, int topN, List<String> actionTypes) {
        int safeLookback = lookbackMinutes <= 0 ? 60 : lookbackMinutes;
        int safeTopN = topN <= 0 ? 10 : Math.min(topN, 100);
        List<String> selectedActions = normalizeActionTypes(actionTypes);
        LocalDateTime from = LocalDateTime.now().minusMinutes(safeLookback);

        Map<String, Integer> userEventCounts = new HashMap<>();
        for (String action : selectedActions) {
            List<OperationAuditLogEntity> logs = operationAuditLogRepository.findByActionTypeOrderByCreatedAtDesc(action);
            for (OperationAuditLogEntity log : logs) {
                if (log.getCreatedAt().isBefore(from)) break;
                userEventCounts.merge(log.getTargetId(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> users = userEventCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(safeTopN)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("username", entry.getKey());
                    row.put("eventCount", entry.getValue());
                    return row;
                })
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("lookbackMinutes", safeLookback);
        data.put("topN", safeTopN);
        data.put("actions", selectedActions);
        data.put("users", users);
        return data;
    }

    // ==================== 导出任务 ====================

    @Transactional(readOnly = true)
    public Map<String, Object> buildSecurityExportPayload(
            String type, int lookbackMinutes, int topN, List<String> actionTypes
    ) {
        return switch (type) {
            case "timeline" -> adminSecurityEventsTimeline(lookbackMinutes, actionTypes);
            case "risk-users-top" -> adminSecurityRiskUsersTop(lookbackMinutes, topN, actionTypes);
            case "summary" -> adminSecurityEventsSummary(lookbackMinutes);
            default -> throw new BizException("不支持的导出类型: " + type, ErrorCode.PARAM_INVALID);
        };
    }

    public String renderSecurityExportContent(String type, String format, Map<String, Object> payload) {
        if ("csv".equalsIgnoreCase(format)) {
            return renderCsv(type, payload);
        }
        return renderJson(payload);
    }

    @Transactional
    public Map<String, Object> createSecurityExportTask(
            String type, String format, int lookbackMinutes, int topN,
            List<String> actionTypes, String idempotencyKey
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            var existing = authExportTaskRepository.findTopByIdempotencyKeyOrderByCreatedAtDesc(normalizedKey);
            if (existing.isPresent()) {
                String status = existing.get().getStatus();
                if ("RUNNING".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status)) {
                    Map<String, Object> reused = getSecurityExportTask(existing.get().getTaskId());
                    reused.put("reused", true);
                    return reused;
                }
            }
        }
        long runningCount = authExportTaskRepository.countByStatus("RUNNING");
        if (runningCount >= Math.max(exportTaskMaxRunning, 1)) {
            throw new BizException("导出任务过多，请稍后重试", ErrorCode.SYS_INTERNAL_ERROR);
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        AuthExportTaskEntity task = new AuthExportTaskEntity();
        task.setTaskId(taskId);
        task.setIdempotencyKey(normalizedKey);
        task.setExportType(type);
        task.setExportFormat(format);
        task.setStatus("RUNNING");
        task.setRetryCount(0);
        task.setMaxRetry(Math.max(exportTaskDefaultMaxRetry, 0));
        task.setErrorCode(ERROR_CODE_NONE);
        task.setCreatedAt(LocalDateTime.now());
        authExportTaskRepository.save(task);
        logAction(ACTION_EXPORT_TASK_CREATED, taskId,
                "type=" + type + ", format=" + format + ", idempotencyKey=" + normalizedKey);
        executeExportTask(task, lookbackMinutes, topN, actionTypes);
        return getSecurityExportTask(taskId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSecurityExportTask(String taskId) {
        AuthExportTaskEntity task = authExportTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BizException("导出任务不存在", ErrorCode.ORDER_NOT_FOUND));
        return toTaskMap(task);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSecurityExportTaskDownload(String taskId) {
        AuthExportTaskEntity task = authExportTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BizException("导出任务不存在", ErrorCode.ORDER_NOT_FOUND));
        if (!"SUCCESS".equalsIgnoreCase(task.getStatus())) {
            throw new BizException("导出任务尚未完成", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("fileName", task.getFileName());
        data.put("format", task.getExportFormat());
        data.put("content", task.getContentText());
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listSecurityExportTasks(int page, int size, String status) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuthExportTaskEntity> taskPage;
        if (status == null || status.isBlank()) {
            taskPage = authExportTaskRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            taskPage = authExportTaskRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pageable);
        }
        List<Map<String, Object>> items = taskPage.getContent().stream().map(this::toTaskMap).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("totalElements", taskPage.getTotalElements());
        data.put("totalPages", taskPage.getTotalPages());
        data.put("items", items);
        return data;
    }

    @Transactional
    public Map<String, Object> cleanupSecurityExportTasks(int retainDays) {
        int safeRetainDays = retainDays <= 0 ? exportTaskRetainDays : retainDays;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeRetainDays);
        int deleted = authExportTaskRepository.deleteFinishedTasksBefore(cutoff);
        Map<String, Object> data = new HashMap<>();
        data.put("retainDays", safeRetainDays);
        data.put("cutoff", cutoff.toString());
        data.put("deletedCount", deleted);
        return data;
    }

    @Scheduled(fixedDelayString = "${security.auth.export-task.cleanup-fixed-delay-ms:3600000}")
    @Transactional
    public void scheduledCleanupSecurityExportTasks() {
        cleanupSecurityExportTasks(exportTaskRetainDays);
        markTimeoutRunningExportTasks();
    }

    @Transactional
    public Map<String, Object> retrySecurityExportTask(String taskId, int lookbackMinutes, int topN, List<String> actionTypes) {
        AuthExportTaskEntity task = authExportTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BizException("导出任务不存在", ErrorCode.ORDER_NOT_FOUND));
        if ("RUNNING".equalsIgnoreCase(task.getStatus())) {
            throw new BizException("任务仍在运行，不能重试", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? 0 : task.getMaxRetry();
        if (retryCount >= maxRetry) {
            throw new BizException("重试次数已达上限", ErrorCode.ORDER_STATUS_CONFLICT);
        }
        task.setStatus("RUNNING");
        task.setRetryCount(retryCount + 1);
        task.setErrorCode(ERROR_CODE_NONE);
        task.setErrorMessage(null);
        task.setFinishedAt(null);
        authExportTaskRepository.save(task);
        logAction(ACTION_EXPORT_TASK_RETRY, taskId,
                "retryCount=" + task.getRetryCount() + ", maxRetry=" + maxRetry);
        executeExportTask(task, lookbackMinutes, topN, actionTypes);
        return getSecurityExportTask(taskId);
    }

    @Transactional
    public void markTimeoutRunningExportTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(exportTaskRunningTimeoutMinutes, 1));
        List<AuthExportTaskEntity> runningTasks = authExportTaskRepository.findByStatusOrderByCreatedAtAsc("RUNNING");
        for (AuthExportTaskEntity task : runningTasks) {
            if (task.getCreatedAt() != null && task.getCreatedAt().isBefore(cutoff)) {
                task.setStatus("FAILED");
                task.setErrorCode(ERROR_CODE_TASK_TIMEOUT);
                task.setErrorMessage("任务运行超时自动终止");
                task.setFinishedAt(LocalDateTime.now());
                authExportTaskRepository.save(task);
                logAction(ACTION_EXPORT_TASK_TIMEOUT, task.getTaskId(),
                        "createdAt=" + task.getCreatedAt());
            }
        }
    }

    // ==================== 内部方法 ====================

    private List<String> normalizeActionTypes(List<String> actionTypes) {
        if (actionTypes == null || actionTypes.isEmpty()) {
            return SECURITY_EVENT_ACTIONS;
        }
        List<String> selected = actionTypes.stream()
                .filter(action -> action != null && !action.isBlank())
                .map(String::trim)
                .distinct()
                .filter(SECURITY_EVENT_ACTIONS::contains)
                .toList();
        return selected.isEmpty() ? SECURITY_EVENT_ACTIONS : selected;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        return idempotencyKey.trim();
    }

    private Map<String, Object> toTaskMap(AuthExportTaskEntity task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", task.getTaskId());
        row.put("idempotencyKey", task.getIdempotencyKey());
        row.put("type", task.getExportType());
        row.put("format", task.getExportFormat());
        row.put("status", task.getStatus());
        row.put("retryCount", task.getRetryCount());
        row.put("maxRetry", task.getMaxRetry());
        row.put("errorCode", task.getErrorCode());
        row.put("fileName", task.getFileName());
        row.put("errorMessage", task.getErrorMessage());
        row.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
        row.put("finishedAt", task.getFinishedAt() != null ? task.getFinishedAt().toString() : null);
        return row;
    }

    private void executeExportTask(AuthExportTaskEntity task, int lookbackMinutes, int topN, List<String> actionTypes) {
        try {
            Map<String, Object> payload = buildSecurityExportPayload(
                    task.getExportType(), lookbackMinutes, topN, actionTypes);
            String content = renderSecurityExportContent(task.getExportType(), task.getExportFormat(), payload);
            task.setContentText(content);
            task.setFileName("security-events-" + task.getExportType() + "."
                    + ("csv".equalsIgnoreCase(task.getExportFormat()) ? "csv" : "json"));
            task.setStatus("SUCCESS");
            task.setErrorCode(ERROR_CODE_NONE);
            task.setFinishedAt(LocalDateTime.now());
            authExportTaskRepository.save(task);
            logAction(ACTION_EXPORT_TASK_SUCCESS, task.getTaskId(),
                    "type=" + task.getExportType() + ", format=" + task.getExportFormat());
        } catch (RuntimeException ex) {
            task.setStatus("FAILED");
            task.setErrorCode(ERROR_CODE_EXECUTION_FAILED);
            task.setErrorMessage(ex.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            authExportTaskRepository.save(task);
            logAction(ACTION_EXPORT_TASK_FAILED, task.getTaskId(), "error=" + ex.getMessage());
        }
    }

    private String renderJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("json serialize failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String renderCsv(String type, Map<String, Object> payload) {
        if ("summary".equals(type)) {
            Map<String, Integer> counts = (Map<String, Integer>) payload.getOrDefault("counts", new LinkedHashMap<>());
            StringBuilder sb = new StringBuilder("actionType,count\n");
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                sb.append(e.getKey()).append(",").append(e.getValue()).append("\n");
            }
            return sb.toString();
        }
        if ("risk-users-top".equals(type)) {
            List<Map<String, Object>> users = (List<Map<String, Object>>) payload.getOrDefault("users", List.of());
            StringBuilder sb = new StringBuilder("username,eventCount\n");
            for (Map<String, Object> user : users) {
                sb.append(user.getOrDefault("username", "")).append(",")
                        .append(user.getOrDefault("eventCount", 0)).append("\n");
            }
            return sb.toString();
        }
        List<Map<String, Object>> points = (List<Map<String, Object>>) payload.getOrDefault("points", List.of());
        StringBuilder sb = new StringBuilder("minute,total,countsJson\n");
        for (Map<String, Object> point : points) {
            sb.append(point.getOrDefault("minute", "")).append(",")
                    .append(point.getOrDefault("total", 0)).append(",")
                    .append("\"").append(renderJson((Map<String, Object>) point.getOrDefault("counts", Map.of())).replace("\"", "\"\"")).append("\"")
                    .append("\n");
        }
        return sb.toString();
    }

    private String buildSecurityRecommendation(Map<String, Integer> counts) {
        if (counts.getOrDefault(ACTION_REFRESH_REPLAY_BLOCKED, 0) >= 3) {
            return "检测到较多 refresh 重放，建议提升风控等级并检查异常设备";
        }
        int exportFailures = counts.getOrDefault(ACTION_EXPORT_TASK_FAILED, 0)
                + counts.getOrDefault(ACTION_EXPORT_TASK_TIMEOUT, 0);
        if (exportFailures >= 3) {
            return "导出任务失败/超时偏高，建议检查导出参数规模、数据库负载与调度窗口";
        }
        return "安全事件总体平稳";
    }

    private void logAction(String actionType, String targetId, String detail) {
        OperationAuditLogEntity log = new OperationAuditLogEntity();
        log.setActionType(actionType);
        log.setTargetType(TARGET_TYPE_AUTH_EXPORT_TASK);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        operationAuditLogRepository.save(log);
    }
}
