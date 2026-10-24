package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M2 站内通知（OPS-050 ~ OPS-052）。
 */
@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthApplicationService authApplicationService;

    public NotificationController(NotificationService notificationService, AuthApplicationService authApplicationService) {
        this.notificationService = notificationService;
        this.authApplicationService = authApplicationService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(notificationService.listMine(authApplicationService.requireUserId(jwt), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(notificationService.unreadCount(authApplicationService.requireUserId(jwt)));
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<Map<String, Object>> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long notificationId
    ) {
        return ApiResponse.ok(notificationService.markRead(authApplicationService.requireUserId(jwt), notificationId));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Object>> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(notificationService.markAllRead(authApplicationService.requireUserId(jwt)));
    }
}
