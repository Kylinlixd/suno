package com.suno.mall.controller;

import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.common.ApiResponse;
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
    private final com.suno.mall.service.SmsCodeService smsCodeService;

    public AuthController(AuthApplicationService authApplicationService,
                          com.suno.mall.service.SmsCodeService smsCodeService) {
        this.authApplicationService = authApplicationService;
        this.smsCodeService = smsCodeService;
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

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(authApplicationService.register(
                request.username(),
                request.password(),
                request.deviceId()
        ));
    }

    /** IDN-009 发送短信验证码 */
    @PostMapping("/sms/send")
    public ApiResponse<Map<String, Object>> sendSmsCode(@RequestBody SmsSendRequest request) {
        return ApiResponse.ok(smsCodeService.send(request.phone()));
    }

    /** IDN-010 手机号验证码登录（无账户自动注册） */
    @PostMapping("/sms/login")
    public ApiResponse<Map<String, Object>> smsLogin(@RequestBody SmsLoginRequest request) {
        return ApiResponse.ok(smsCodeService.loginBySms(request.phone(), request.code(), request.deviceId()));
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            String deviceId
    ) {
    }

    public record RegisterRequest(
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

    public record SmsSendRequest(@NotBlank String phone) {
    }

    public record SmsLoginRequest(@NotBlank String phone, @NotBlank String code, String deviceId) {
    }
}
