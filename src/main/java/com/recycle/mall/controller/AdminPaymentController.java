package com.recycle.mall.controller;

import com.recycle.mall.service.RecycleApplicationService;
import com.recycle.mall.common.ApiResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/payment")
public class AdminPaymentController {

    private final RecycleApplicationService recycleApplicationService;

    public AdminPaymentController(RecycleApplicationService recycleApplicationService) {
        this.recycleApplicationService = recycleApplicationService;
    }

    @GetMapping("/callback-logs")
    public ApiResponse<Map<String, Object>> pageCallbackLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String callbackStatus
    ) {
        return ApiResponse.ok(recycleApplicationService.pagePaymentCallbackLogs(page, size, callbackStatus));
    }

    @PostMapping("/callback-logs/replay")
    public ApiResponse<Map<String, Object>> replayCallback(@RequestBody ReplayRequest request) {
        return ApiResponse.ok(recycleApplicationService.replayPaymentCallback(request.callbackLogId()));
    }

    @PostMapping("/callback-logs/replay/enqueue")
    public ApiResponse<Map<String, Object>> enqueueReplay(@RequestBody ReplayRequest request) {
        return ApiResponse.ok(recycleApplicationService.enqueueReplayTask(request.callbackLogId()));
    }

    @PostMapping("/callback-logs/replay/consume")
    public ApiResponse<Map<String, Object>> consumeReplayQueue(
            @RequestBody ConsumeReplayQueueRequest request
    ) {
        return ApiResponse.ok(recycleApplicationService.consumeReplayTasks(request.maxCount()));
    }

    @GetMapping("/replay-tasks")
    public ApiResponse<Map<String, Object>> pageReplayTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(recycleApplicationService.pageReplayTasks(page, size, status));
    }

    @GetMapping("/replay-tasks/summary")
    public ApiResponse<Map<String, Object>> replayTaskSummary() {
        return ApiResponse.ok(recycleApplicationService.replayTaskSummary());
    }

    @GetMapping("/replay-tasks/query-audit-actions")
    public ApiResponse<Map<String, Object>> replayQueryAuditActions(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestParam(required = false, defaultValue = "zh-CN") String lang
    ) {
        return ApiResponse.ok(recycleApplicationService.replayQueryAuditActions(traceId, lang));
    }

    @GetMapping("/replay-tasks/health")
    public ApiResponse<Map<String, Object>> replayTaskHealth(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId
    ) {
        return ApiResponse.ok(recycleApplicationService.replayTaskHealth(traceId));
    }

    @GetMapping("/replay-tasks/diagnosis")
    public ApiResponse<Map<String, Object>> replayTaskDiagnosis(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId
    ) {
        return ApiResponse.ok(recycleApplicationService.replayTaskDiagnosis(traceId));
    }

    @GetMapping("/replay-tasks/cleanup-performance-check")
    public ApiResponse<Map<String, Object>> replayCleanupPerformanceCheck(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId
    ) {
        return ApiResponse.ok(recycleApplicationService.replayCleanupPerformanceCheck(traceId));
    }

    @PostMapping("/replay-tasks/auto-handle")
    public ApiResponse<Map<String, Object>> replayTaskAutoHandle(
            @RequestBody AutoHandleRequest request,
            @RequestHeader(value = "X-Operator", required = false) String operator,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId
    ) {
        boolean allowRequeueDead = request.allowRequeueDead() != null && request.allowRequeueDead();
        int consumeMaxCount = request.consumeMaxCount() == null ? 50 : request.consumeMaxCount();
        int requeueMaxCount = request.requeueMaxCount() == null ? 50 : request.requeueMaxCount();
        return ApiResponse.ok(recycleApplicationService.replayTaskAutoHandle(
                allowRequeueDead,
                consumeMaxCount,
                requeueMaxCount,
                operator,
                traceId
        ));
    }

    @GetMapping("/replay-tasks/auto-handle-idempotency")
    public ApiResponse<Map<String, Object>> pageReplayAutoHandleIdempotencyRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt
    ) {
        return ApiResponse.ok(recycleApplicationService.pageReplayAutoHandleIdempotencyRecords(
                page,
                size,
                traceId,
                startAt,
                endAt
        ));
    }

    @GetMapping("/replay-tasks/auto-handle-idempotency/detail")
    public ApiResponse<Map<String, Object>> getReplayAutoHandleIdempotencyDetail(
            @RequestParam String traceId
    ) {
        return ApiResponse.ok(recycleApplicationService.getReplayAutoHandleIdempotencyDetail(traceId));
    }

    @PostMapping("/replay-tasks/auto-handle-idempotency/delete")
    public ApiResponse<Map<String, Object>> deleteReplayAutoHandleIdempotencyByTraceId(
            @RequestBody DeleteAutoHandleIdempotencyByTraceIdRequest request
    ) {
        return ApiResponse.ok(recycleApplicationService.deleteReplayAutoHandleIdempotencyByTraceId(request.traceId()));
    }

    @PostMapping("/replay-tasks/auto-handle-idempotency/delete-before")
    public ApiResponse<Map<String, Object>> batchDeleteReplayAutoHandleIdempotencyBefore(
            @RequestBody DeleteAutoHandleIdempotencyBeforeRequest request
    ) {
        return ApiResponse.ok(recycleApplicationService.batchDeleteReplayAutoHandleIdempotencyBefore(request.beforeTime()));
    }

    @PostMapping("/replay-tasks/auto-handle-idempotency/cleanup")
    public ApiResponse<Map<String, Object>> cleanupReplayAutoHandleIdempotencyRecords(
            @RequestBody CleanupAutoHandleIdempotencyRequest request
    ) {
        int retainDays = request.retainDays() == null ? 7 : request.retainDays();
        return ApiResponse.ok(recycleApplicationService.cleanupReplayAutoHandleIdempotencyRecords(retainDays));
    }

    @PostMapping("/replay-tasks/requeue")
    public ApiResponse<Map<String, Object>> requeueTask(@RequestBody RequeueTaskRequest request) {
        return ApiResponse.ok(recycleApplicationService.requeueReplayTask(request.taskId()));
    }

    @PostMapping("/replay-tasks/requeue/dead")
    public ApiResponse<Map<String, Object>> batchRequeueDeadTasks(@RequestBody ConsumeReplayQueueRequest request) {
        return ApiResponse.ok(recycleApplicationService.batchRequeueDeadTasks(request.maxCount()));
    }

    public record ReplayRequest(
            @NotNull Long callbackLogId
    ) {
    }

    public record ConsumeReplayQueueRequest(
            @NotNull Integer maxCount
    ) {
    }

    public record RequeueTaskRequest(
            @NotNull Long taskId
    ) {
    }

    public record AutoHandleRequest(
            Boolean allowRequeueDead,
            Integer consumeMaxCount,
            Integer requeueMaxCount
    ) {
    }

    public record DeleteAutoHandleIdempotencyByTraceIdRequest(
            @NotNull String traceId
    ) {
    }

    public record DeleteAutoHandleIdempotencyBeforeRequest(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeTime
    ) {
    }

    public record CleanupAutoHandleIdempotencyRequest(
            Integer retainDays
    ) {
    }
}
