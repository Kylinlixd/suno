package com.recycle.mall.service;

import com.recycle.mall.common.BizException;
import com.recycle.mall.common.ErrorCode;
import com.recycle.mall.entity.AuthRefreshTokenEntity;
import com.recycle.mall.entity.AuthTokenBlacklistEntity;
import com.recycle.mall.entity.OperationAuditLogEntity;
import com.recycle.mall.entity.UserAccountEntity;
import com.recycle.mall.dao.AuthRefreshTokenRepository;
import com.recycle.mall.dao.AuthTokenBlacklistRepository;
import com.recycle.mall.dao.OperationAuditLogRepository;
import com.recycle.mall.dao.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * 认证核心服务
 * <p>
 * 负责登录、刷新令牌、登出、Token 签发与黑名单管理。
 * 会话管理委托 {@link AuthSessionService}，安全事件委托 {@link SecurityEventService}。
 */
@Service
public class AuthApplicationService {

    private static final String TARGET_TYPE_AUTH_SESSION = "AUTH_SESSION";
    private static final String ACTION_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    private static final String ACTION_REFRESH_SUCCESS = "AUTH_REFRESH_SUCCESS";
    private static final String ACTION_REFRESH_REPLAY_BLOCKED = "AUTH_REFRESH_REPLAY_BLOCKED";
    private static final String ACTION_LOGOUT = "AUTH_LOGOUT";

    private final UserAccountRepository userAccountRepository;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final AuthTokenBlacklistRepository authTokenBlacklistRepository;
    private final OperationAuditLogRepository operationAuditLogRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final AuthSessionService authSessionService;
    private final SecurityEventService securityEventService;

    @Value("${security.auth.jwt.expire-minutes:120}")
    private long accessTokenExpireMinutes;

    @Value("${security.auth.jwt.refresh-expire-minutes:10080}")
    private long refreshTokenExpireMinutes;

    public AuthApplicationService(
            UserAccountRepository userAccountRepository,
            AuthRefreshTokenRepository authRefreshTokenRepository,
            AuthTokenBlacklistRepository authTokenBlacklistRepository,
            OperationAuditLogRepository operationAuditLogRepository,
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            AuthSessionService authSessionService,
            SecurityEventService securityEventService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
        this.authTokenBlacklistRepository = authTokenBlacklistRepository;
        this.operationAuditLogRepository = operationAuditLogRepository;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.authSessionService = authSessionService;
        this.securityEventService = securityEventService;
    }

    @Transactional
    public Map<String, Object> login(String username, String password, String deviceId) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password)
        );
        String usernameResolved = authentication.getName();
        String role = resolveRole(authentication);
        UserAccountEntity user = userAccountRepository.findByUsername(usernameResolved)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        Map<String, Object> result = buildAndPersistTokens(user.getId(), usernameResolved, role, normalizedDeviceId);
        logAction(
                ACTION_LOGIN_SUCCESS,
                usernameResolved,
                "deviceId=" + normalizedDeviceId + ", role=" + role
        );
        return result;
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken, String deviceId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException("refreshToken不能为空", ErrorCode.PARAM_INVALID);
        }
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        LocalDateTime now = LocalDateTime.now();
        authRefreshTokenRepository.deleteByExpireAtBefore(now);
        AuthRefreshTokenEntity tokenEntity = authRefreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BizException("refreshToken无效", ErrorCode.AUTH_REFRESH_TOKEN_INVALID));
        if (Boolean.TRUE.equals(tokenEntity.getRevoked())) {
            revokeAllActiveRefreshTokens(tokenEntity.getUsername());
            logAction(
                    ACTION_REFRESH_REPLAY_BLOCKED,
                    tokenEntity.getUsername(),
                    "deviceId=" + tokenEntity.getDeviceId() + ", reason=replay-detected"
            );
            throw new BizException("检测到refresh token重放，已阻断会话", ErrorCode.AUTH_REFRESH_REPLAY_BLOCKED);
        }
        if (tokenEntity.getExpireAt().isBefore(now)) {
            throw new BizException("refreshToken已失效", ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
        }
        if (!normalizedDeviceId.equals(tokenEntity.getDeviceId())) {
            throw new BizException("refreshToken与设备不匹配", ErrorCode.AUTH_REFRESH_TOKEN_DEVICE_MISMATCH);
        }

        tokenEntity.setRevoked(true);
        tokenEntity.setRevokedAt(now);
        authRefreshTokenRepository.save(tokenEntity);

        UserAccountEntity user = userAccountRepository.findByUsername(tokenEntity.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Map<String, Object> result = buildAndPersistTokens(user.getId(), user.getUsername(), user.getRoleCode(), normalizedDeviceId);
        logAction(
                ACTION_REFRESH_SUCCESS,
                user.getUsername(),
                "deviceId=" + normalizedDeviceId
        );
        return result;
    }

    public Map<String, Object> me(Jwt jwt) {
        UserAccountEntity user = userAccountRepository.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRoleCode());
        data.put("accountStatus", user.getAccountStatus());
        data.put("tokenExpireAt", jwt.getExpiresAt() != null ? jwt.getExpiresAt().toString() : null);
        return data;
    }

    @Transactional
    public Map<String, Object> logout(Jwt jwt, @Nullable String refreshToken) {
        Instant now = Instant.now();
        if (jwt.getExpiresAt() != null && jwt.getId() != null) {
            if (!authTokenBlacklistRepository.existsByJti(jwt.getId())) {
                AuthTokenBlacklistEntity blacklistEntity = new AuthTokenBlacklistEntity();
                blacklistEntity.setJti(jwt.getId());
                blacklistEntity.setUsername(jwt.getSubject());
                blacklistEntity.setExpireAt(LocalDateTime.ofInstant(jwt.getExpiresAt(), java.time.ZoneOffset.UTC));
                blacklistEntity.setCreatedAt(LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
                authTokenBlacklistRepository.save(blacklistEntity);
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            authRefreshTokenRepository.findByToken(refreshToken).ifPresent(tokenEntity -> {
                tokenEntity.setRevoked(true);
                tokenEntity.setRevokedAt(LocalDateTime.now());
                authRefreshTokenRepository.save(tokenEntity);
            });
        }
        authTokenBlacklistRepository.deleteByExpireAtBefore(LocalDateTime.now());
        logAction(ACTION_LOGOUT, jwt.getSubject(), "hasRefreshToken=" + (refreshToken != null && !refreshToken.isBlank()));
        Map<String, Object> result = new HashMap<>();
        result.put("loggedOut", true);
        result.put("blacklisted", jwt.getId() != null);
        return result;
    }

    // ==================== 会话管理（委托 AuthSessionService） ====================

    @Transactional(readOnly = true)
    public Map<String, Object> listActiveSessions(Jwt jwt) {
        return authSessionService.listActiveSessions(jwt);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminListUserSessions(String username) {
        return authSessionService.adminListUserSessions(username);
    }

    @Transactional
    public Map<String, Object> revokeDeviceSession(Jwt jwt, String deviceId) {
        return authSessionService.revokeDeviceSession(jwt, deviceId);
    }

    @Transactional
    public Map<String, Object> adminRevokeUserDeviceSession(String username, String deviceId) {
        return authSessionService.adminRevokeUserDeviceSession(username, deviceId);
    }

    @Transactional
    public Map<String, Object> revokeAllSessions(Jwt jwt) {
        return authSessionService.revokeAllSessions(jwt);
    }

    @Transactional
    public Map<String, Object> adminRevokeUserAllSessions(String username) {
        return authSessionService.adminRevokeUserAllSessions(username);
    }

    // ==================== Token 构建 ====================

    private Map<String, Object> buildAndPersistTokens(Long userId, String username, String role, String deviceId) {
        Instant now = Instant.now();
        Instant accessExpireAt = now.plusSeconds(accessTokenExpireMinutes * 60);
        String jti = UUID.randomUUID().toString().replace("-", "");

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("recycle-mall")
                .subject(username)
                .issuedAt(now)
                .expiresAt(accessExpireAt)
                .id(jti)
                .claim("role", role)
                .claim("scope", "mall recycle admin")
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        String refreshToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        LocalDateTime refreshExpireAt = LocalDateTime.now().plusMinutes(refreshTokenExpireMinutes);

        List<AuthRefreshTokenEntity> activeSameDevice =
                authRefreshTokenRepository.findActiveByUsernameAndDeviceId(username, deviceId);
        for (AuthRefreshTokenEntity existed : activeSameDevice) {
            existed.setRevoked(true);
            existed.setRevokedAt(LocalDateTime.now());
            authRefreshTokenRepository.save(existed);
        }

        AuthRefreshTokenEntity refreshTokenEntity = new AuthRefreshTokenEntity();
        refreshTokenEntity.setUserId(userId);
        refreshTokenEntity.setUsername(username);
        refreshTokenEntity.setDeviceId(deviceId);
        refreshTokenEntity.setToken(refreshToken);
        refreshTokenEntity.setExpireAt(refreshExpireAt);
        refreshTokenEntity.setRevoked(false);
        refreshTokenEntity.setCreatedAt(LocalDateTime.now());
        authRefreshTokenRepository.save(refreshTokenEntity);

        Map<String, Object> data = new HashMap<>();
        data.put("tokenType", "Bearer");
        data.put("accessToken", accessToken);
        data.put("expiresIn", accessTokenExpireMinutes * 60);
        data.put("refreshToken", refreshToken);
        data.put("refreshExpiresIn", refreshTokenExpireMinutes * 60);
        data.put("deviceId", deviceId);
        data.put("username", username);
        data.put("role", role);
        return data;
    }

    private String normalizeDeviceId(String deviceId) {
        return authSessionService.normalizeDeviceId(deviceId);
    }

    private void revokeAllActiveRefreshTokens(String username) {
        List<AuthRefreshTokenEntity> activeTokens = authRefreshTokenRepository.findByUsernameAndRevokedFalse(username);
        for (AuthRefreshTokenEntity tokenEntity : activeTokens) {
            tokenEntity.setRevoked(true);
            tokenEntity.setRevokedAt(LocalDateTime.now());
            authRefreshTokenRepository.save(tokenEntity);
        }
    }

    private String resolveRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .orElse("USER");
    }

    private void logAction(String actionType, String targetId, String detail) {
        logAction(actionType, TARGET_TYPE_AUTH_SESSION, targetId, detail);
    }

    private void logAction(String actionType, String targetType, String targetId, String detail) {
        OperationAuditLogEntity log = new OperationAuditLogEntity();
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        operationAuditLogRepository.save(log);
    }

    // ==================== 安全事件（委托 SecurityEventService） ====================

    @Transactional(readOnly = true)
    public Map<String, Object> adminSecurityEventsSummary(int lookbackMinutes) {
        return securityEventService.adminSecurityEventsSummary(lookbackMinutes);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSecurityEventsTimeline(int lookbackMinutes, List<String> actionTypes) {
        return securityEventService.adminSecurityEventsTimeline(lookbackMinutes, actionTypes);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSecurityRiskUsersTop(int lookbackMinutes, int topN, List<String> actionTypes) {
        return securityEventService.adminSecurityRiskUsersTop(lookbackMinutes, topN, actionTypes);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildSecurityExportPayload(String type, int lookbackMinutes, int topN, List<String> actionTypes) {
        return securityEventService.buildSecurityExportPayload(type, lookbackMinutes, topN, actionTypes);
    }

    public String renderSecurityExportContent(String type, String format, Map<String, Object> payload) {
        return securityEventService.renderSecurityExportContent(type, format, payload);
    }

    @Transactional
    public Map<String, Object> createSecurityExportTask(String type, String format, int lookbackMinutes, int topN, List<String> actionTypes, String idempotencyKey) {
        return securityEventService.createSecurityExportTask(type, format, lookbackMinutes, topN, actionTypes, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSecurityExportTask(String taskId) {
        return securityEventService.getSecurityExportTask(taskId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSecurityExportTaskDownload(String taskId) {
        return securityEventService.getSecurityExportTaskDownload(taskId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listSecurityExportTasks(int page, int size, String status) {
        return securityEventService.listSecurityExportTasks(page, size, status);
    }

    @Transactional
    public Map<String, Object> cleanupSecurityExportTasks(int retainDays) {
        return securityEventService.cleanupSecurityExportTasks(retainDays);
    }

    @Transactional
    public Map<String, Object> retrySecurityExportTask(String taskId, int lookbackMinutes, int topN, List<String> actionTypes) {
        return securityEventService.retrySecurityExportTask(taskId, lookbackMinutes, topN, actionTypes);
    }
}

