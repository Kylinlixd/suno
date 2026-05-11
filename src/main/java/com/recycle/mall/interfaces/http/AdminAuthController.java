package com.recycle.mall.interfaces.http;

import com.recycle.mall.application.AuthApplicationService;
import com.recycle.mall.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AuthApplicationService authApplicationService;

    public AdminAuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @GetMapping("/sessions")
    public ApiResponse<Map<String, Object>> listUserSessions(@RequestParam @NotBlank String username) {
        return ApiResponse.ok(authApplicationService.adminListUserSessions(username));
    }

    @GetMapping("/security-events/summary")
    public ApiResponse<Map<String, Object>> securityEventsSummary(
            @RequestParam(defaultValue = "60") int lookbackMinutes
    ) {
        return ApiResponse.ok(authApplicationService.adminSecurityEventsSummary(lookbackMinutes));
    }

    @GetMapping("/security-events/timeline")
    public ApiResponse<Map<String, Object>> securityEventsTimeline(
            @RequestParam(defaultValue = "60") int lookbackMinutes,
            @RequestParam(required = false) List<String> actionTypes
    ) {
        return ApiResponse.ok(authApplicationService.adminSecurityEventsTimeline(lookbackMinutes, actionTypes));
    }

    @GetMapping("/security-events/risk-users-top")
    public ApiResponse<Map<String, Object>> securityRiskUsersTop(
            @RequestParam(defaultValue = "60") int lookbackMinutes,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) List<String> actionTypes
    ) {
        return ApiResponse.ok(authApplicationService.adminSecurityRiskUsersTop(
                lookbackMinutes,
                topN,
                actionTypes
        ));
    }

    @GetMapping("/security-events/export")
    public ResponseEntity<String> securityEventsExport(
            @RequestParam(defaultValue = "summary") String type,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "60") int lookbackMinutes,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) List<String> actionTypes
    ) {
        Map<String, Object> payload = authApplicationService.buildSecurityExportPayload(
                type,
                lookbackMinutes,
                topN,
                actionTypes
        );
        String content = authApplicationService.renderSecurityExportContent(type, format, payload);
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"security-events-" + type + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(content);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"security-events-" + type + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
    }

    @PostMapping("/security-events/export/tasks")
    public ApiResponse<Map<String, Object>> createSecurityEventsExportTask(
            @RequestBody CreateExportTaskRequest request
    ) {
        return ApiResponse.ok(authApplicationService.createSecurityExportTask(
                request.type(),
                request.format(),
                request.lookbackMinutes(),
                request.topN(),
                request.actionTypes(),
                request.idempotencyKey()
        ));
    }

    @PostMapping("/security-events/export/tasks/{taskId}/retry")
    public ApiResponse<Map<String, Object>> retrySecurityEventsExportTask(
            @org.springframework.web.bind.annotation.PathVariable String taskId,
            @RequestBody(required = false) RetryExportTaskRequest request
    ) {
        int lookbackMinutes = request == null ? 60 : request.lookbackMinutes();
        int topN = request == null ? 10 : request.topN();
        List<String> actionTypes = request == null ? null : request.actionTypes();
        return ApiResponse.ok(authApplicationService.retrySecurityExportTask(taskId, lookbackMinutes, topN, actionTypes));
    }

    @GetMapping("/security-events/export/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getSecurityEventsExportTask(
            @org.springframework.web.bind.annotation.PathVariable String taskId
    ) {
        return ApiResponse.ok(authApplicationService.getSecurityExportTask(taskId));
    }

    @GetMapping("/security-events/export/tasks/{taskId}/download")
    public ResponseEntity<String> downloadSecurityEventsExportTask(
            @org.springframework.web.bind.annotation.PathVariable String taskId
    ) {
        Map<String, Object> data = authApplicationService.getSecurityExportTaskDownload(taskId);
        String fileName = String.valueOf(data.getOrDefault("fileName", "security-events-export.txt"));
        String format = String.valueOf(data.getOrDefault("format", "json"));
        String content = String.valueOf(data.getOrDefault("content", ""));
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(content);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
    }

    @GetMapping("/security-events/export/tasks")
    public ApiResponse<Map<String, Object>> listSecurityEventsExportTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(authApplicationService.listSecurityExportTasks(page, size, status));
    }

    @PostMapping("/security-events/export/tasks/cleanup")
    public ApiResponse<Map<String, Object>> cleanupSecurityEventsExportTasks(
            @RequestBody(required = false) CleanupExportTasksRequest request
    ) {
        int retainDays = request == null ? 0 : request.retainDays();
        return ApiResponse.ok(authApplicationService.cleanupSecurityExportTasks(retainDays));
    }

    @PostMapping("/sessions/revoke-device")
    public ApiResponse<Map<String, Object>> revokeUserDevice(@RequestBody AdminRevokeDeviceRequest request) {
        return ApiResponse.ok(authApplicationService.adminRevokeUserDeviceSession(
                request.username(),
                request.deviceId()
        ));
    }

    @PostMapping("/sessions/revoke-all")
    public ApiResponse<Map<String, Object>> revokeUserAll(@RequestBody AdminRevokeAllRequest request) {
        return ApiResponse.ok(authApplicationService.adminRevokeUserAllSessions(request.username()));
    }

    public record AdminRevokeDeviceRequest(
            @NotBlank String username,
            @NotBlank String deviceId
    ) {
    }

    public record AdminRevokeAllRequest(@NotBlank String username) {
    }

    public record CreateExportTaskRequest(
            @NotBlank String type,
            @NotBlank String format,
            int lookbackMinutes,
            int topN,
            List<String> actionTypes,
            String idempotencyKey
    ) {
    }

    public record CleanupExportTasksRequest(int retainDays) {
    }

    public record RetryExportTaskRequest(
            int lookbackMinutes,
            int topN,
            List<String> actionTypes
    ) {
    }
}
