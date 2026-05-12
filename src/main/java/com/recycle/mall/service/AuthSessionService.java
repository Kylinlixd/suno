package com.recycle.mall.service;

import com.recycle.mall.entity.AuthRefreshTokenEntity;
import com.recycle.mall.dao.AuthRefreshTokenRepository;
import com.recycle.mall.dao.OperationAuditLogRepository;
import com.recycle.mall.entity.OperationAuditLogEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理服务
 * <p>
 * 负责设备会话查询、按设备下线、全设备下线等会话生命周期管理。
 */
@Service
public class AuthSessionService {

    private static final String TARGET_TYPE_AUTH_SESSION = "AUTH_SESSION";
    private static final String ACTION_SESSION_REVOKE_DEVICE = "AUTH_SESSION_REVOKE_DEVICE";
    private static final String ACTION_SESSION_REVOKE_ALL = "AUTH_SESSION_REVOKE_ALL";
    private static final String ACTION_ADMIN_SESSION_REVOKE_DEVICE = "AUTH_ADMIN_SESSION_REVOKE_DEVICE";
    private static final String ACTION_ADMIN_SESSION_REVOKE_ALL = "AUTH_ADMIN_SESSION_REVOKE_ALL";
    private static final String ACTION_ADMIN_SESSION_QUERY = "AUTH_ADMIN_SESSION_QUERY";
    private static final String DEFAULT_DEVICE_ID = "web-default";

    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final OperationAuditLogRepository operationAuditLogRepository;

    public AuthSessionService(
            AuthRefreshTokenRepository authRefreshTokenRepository,
            OperationAuditLogRepository operationAuditLogRepository
    ) {
        this.authRefreshTokenRepository = authRefreshTokenRepository;
        this.operationAuditLogRepository = operationAuditLogRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listActiveSessions(Jwt jwt) {
        return listActiveSessionsByUsername(jwt.getSubject());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminListUserSessions(String username) {
        logAction(ACTION_ADMIN_SESSION_QUERY, username, "operator=admin");
        return listActiveSessionsByUsername(username);
    }

    @Transactional
    public Map<String, Object> revokeDeviceSession(Jwt jwt, String deviceId) {
        Map<String, Object> result = revokeDeviceSessionByUsername(jwt.getSubject(), deviceId);
        logAction(
                ACTION_SESSION_REVOKE_DEVICE,
                jwt.getSubject(),
                "deviceId=" + normalizeDeviceId(deviceId) + ", revokedCount=" + result.get("revokedCount")
        );
        return result;
    }

    @Transactional
    public Map<String, Object> adminRevokeUserDeviceSession(String username, String deviceId) {
        Map<String, Object> result = revokeDeviceSessionByUsername(username, deviceId);
        logAction(
                ACTION_ADMIN_SESSION_REVOKE_DEVICE,
                username,
                "deviceId=" + normalizeDeviceId(deviceId) + ", revokedCount=" + result.get("revokedCount")
        );
        return result;
    }

    @Transactional
    public Map<String, Object> revokeAllSessions(Jwt jwt) {
        Map<String, Object> result = revokeAllSessionsByUsername(jwt.getSubject());
        logAction(ACTION_SESSION_REVOKE_ALL, jwt.getSubject(), "revokedCount=" + result.get("revokedCount"));
        return result;
    }

    @Transactional
    public Map<String, Object> adminRevokeUserAllSessions(String username) {
        Map<String, Object> result = revokeAllSessionsByUsername(username);
        logAction(ACTION_ADMIN_SESSION_REVOKE_ALL, username, "revokedCount=" + result.get("revokedCount"));
        return result;
    }

    // ==================== 内部方法 ====================

    private Map<String, Object> listActiveSessionsByUsername(String username) {
        List<AuthRefreshTokenEntity> sessions =
                authRefreshTokenRepository.findByUsernameAndRevokedFalseOrderByCreatedAtDesc(username);
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (AuthRefreshTokenEntity session : sessions) {
            Map<String, Object> row = new HashMap<>();
            row.put("deviceId", session.getDeviceId());
            row.put("createdAt", session.getCreatedAt().toString());
            row.put("expireAt", session.getExpireAt().toString());
            row.put("revoked", session.getRevoked());
            items.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("total", items.size());
        data.put("sessions", items);
        return data;
    }

    private Map<String, Object> revokeDeviceSessionByUsername(String username, String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        List<AuthRefreshTokenEntity> activeSameDevice =
                authRefreshTokenRepository.findActiveByUsernameAndDeviceId(username, normalizedDeviceId);
        int revokedCount = 0;
        for (AuthRefreshTokenEntity tokenEntity : activeSameDevice) {
            tokenEntity.setRevoked(true);
            tokenEntity.setRevokedAt(LocalDateTime.now());
            authRefreshTokenRepository.save(tokenEntity);
            revokedCount++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("deviceId", normalizedDeviceId);
        result.put("revokedCount", revokedCount);
        return result;
    }

    private Map<String, Object> revokeAllSessionsByUsername(String username) {
        List<AuthRefreshTokenEntity> activeTokens =
                authRefreshTokenRepository.findByUsernameAndRevokedFalse(username);
        int revokedCount = 0;
        for (AuthRefreshTokenEntity tokenEntity : activeTokens) {
            tokenEntity.setRevoked(true);
            tokenEntity.setRevokedAt(LocalDateTime.now());
            authRefreshTokenRepository.save(tokenEntity);
            revokedCount++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("revokedCount", revokedCount);
        return result;
    }

    String normalizeDeviceId(@Nullable String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return DEFAULT_DEVICE_ID;
        }
        return deviceId.trim();
    }

    private void logAction(String actionType, String targetId, String detail) {
        OperationAuditLogEntity log = new OperationAuditLogEntity();
        log.setActionType(actionType);
        log.setTargetType(TARGET_TYPE_AUTH_SESSION);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        operationAuditLogRepository.save(log);
    }
}
