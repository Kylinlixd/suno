package com.recycle.mall.controller;

import com.recycle.mall.service.AuthApplicationService;
import com.recycle.mall.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authApplicationService.login(
                request.username(),
                request.password(),
                request.deviceId()
        ));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(authApplicationService.me(jwt));
    }

    @GetMapping("/sessions")
    public ApiResponse<Map<String, Object>> sessions(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(authApplicationService.listActiveSessions(jwt));
    }

    @PostMapping("/sessions/revoke-device")
    public ApiResponse<Map<String, Object>> revokeDevice(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RevokeDeviceRequest request
    ) {
        return ApiResponse.ok(authApplicationService.revokeDeviceSession(jwt, request.deviceId()));
    }

    @PostMapping("/sessions/revoke-all")
    public ApiResponse<Map<String, Object>> revokeAll(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(authApplicationService.revokeAllSessions(jwt));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) LogoutRequest request
    ) {
        String refreshToken = request == null ? null : request.refreshToken();
        return ApiResponse.ok(authApplicationService.logout(jwt, refreshToken));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authApplicationService.refresh(
                request.refreshToken(),
                request.deviceId()
        ));
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            String deviceId
    ) {
    }

    public record RefreshTokenRequest(
            @NotBlank String refreshToken,
            String deviceId
    ) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record RevokeDeviceRequest(@NotBlank String deviceId) {
    }
}
