package com.recycle.mall.application;

import com.recycle.mall.infrastructure.entity.AuthRefreshTokenEntity;
import com.recycle.mall.infrastructure.entity.AuthExportTaskEntity;
import com.recycle.mall.infrastructure.entity.AuthTokenBlacklistEntity;
import com.recycle.mall.infrastructure.entity.OperationAuditLogEntity;
import com.recycle.mall.infrastructure.entity.UserAccountEntity;
import com.recycle.mall.infrastructure.repository.AuthRefreshTokenRepository;
import com.recycle.mall.infrastructure.repository.AuthExportTaskRepository;
import com.recycle.mall.infrastructure.repository.AuthTokenBlacklistRepository;
import com.recycle.mall.infrastructure.repository.OperationAuditLogRepository;
import com.recycle.mall.infrastructure.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;

@Service
public class AuthApplicationService {
    private static final String DEFAULT_DEVICE_ID = "web-default";
    private static final String TARGET_TYPE_AUTH_SESSION = "AUTH_SESSION";
    private static final String TARGET_TYPE_AUTH_EXPORT_TASK = "AUTH_EXPORT_TASK";
    private static final String ACTION_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    private static final String ACTION_REFRESH_SUCCESS = "AUTH_REFRESH_SUCCESS";
    private static final String ACTION_REFRESH_REPLAY_BLOCKED = "AUTH_REFRESH_REPLAY_BLOCKED";
    private static final String ACTION_LOGOUT = "AUTH_LOGOUT";
    private static final String ACTION_SESSION_REVOKE_DEVICE = "AUTH_SESSION_REVOKE_DEVICE";
    private static final String ACTION_SESSION_REVOKE_ALL = "AUTH_SESSION_REVOKE_ALL";
    private static final String ACTION_ADMIN_SESSION_REVOKE_DEVICE = "AUTH_ADMIN_SESSION_REVOKE_DEVICE";
    private static final String ACTION_ADMIN_SESSION_REVOKE_ALL = "AUTH_ADMIN_SESSION_REVOKE_ALL";
    private static final String ACTION_ADMIN_SESSION_QUERY = "AUTH_ADMIN_SESSION_QUERY";
    private static final String ACTION_EXPORT_TASK_CREATED = "AUTH_EXPORT_TASK_CREATED";
    private static final String ACTION_EXPORT_TASK_SUCCESS = "AUTH_EXPORT_TASK_SUCCESS";
    private static final String ACTION_EXPORT_TASK_FAILED = "AUTH_EXPORT_TASK_FAILED";
    private static final String ACTION_EXPORT_TASK_RETRY = "AUTH_EXPORT_TASK_RETRY";
    private static final String ACTION_EXPORT_TASK_TIMEOUT = "AUTH_EXPORT_TASK_TIMEOUT";
    private static final String ERROR_CODE_NONE = "NONE";
    private static final String ERROR_CODE_EXECUTION_FAILED = "EXPORT_EXECUTION_FAILED";
    private static final String ERROR_CODE_TASK_TIMEOUT = "EXPORT_TASK_TIMEOUT";
    private static final List<String> SECURITY_EVENT_ACTIONS = List.of(
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

    private final UserAccountRepository userAccountRepository;
    private final AuthExportTaskRepository authExportTaskRepository;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final AuthTokenBlacklistRepository authTokenBlacklistRepository;
    private final OperationAuditLogRepository operationAuditLogRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    @Value("${security.auth.jwt.expire-minutes:120}")
    private long accessTokenExpireMinutes;

    @Value("${security.auth.jwt.refresh-expire-minutes:10080}")
    private long refreshTokenExpireMinutes;

    @Value("${security.auth.export-task.retain-days:7}")
    private int exportTaskRetainDays;

    @Value("${security.auth.export-task.max-running:3}")
    private int exportTaskMaxRunning;

    @Value("${security.auth.export-task.default-max-retry:2}")
    private int exportTaskDefaultMaxRetry;

    @Value("${security.auth.export-task.running-timeout-minutes:10}")
    private int exportTaskRunningTimeoutMinutes;

    public AuthApplicationService(
            UserAccountRepository userAccountRepository,
            AuthExportTaskRepository authExportTaskRepository,
            AuthRefreshTokenRepository authRefreshTokenRepository,
            AuthTokenBlacklistRepository authTokenBlacklistRepository,
            OperationAuditLogRepository operationAuditLogRepository,
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.authExportTaskRepository = authExportTaskRepository;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
        this.authTokenBlacklistRepository = authTokenBlacklistRepository;
        this.operationAuditLogRepository = operationAuditLogRepository;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
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
            throw new IllegalArgumentException("refreshToken不能为空");
        }
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        LocalDateTime now = LocalDateTime.now();
        authRefreshTokenRepository.deleteByExpireAtBefore(now);
        AuthRefreshTokenEntity tokenEntity = authRefreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("refreshToken无效"));
        if (Boolean.TRUE.equals(tokenEntity.getRevoked())) {
            revokeAllActiveRefreshTokens(tokenEntity.getUsername());
            logAction(
                    ACTION_REFRESH_REPLAY_BLOCKED,
                    tokenEntity.getUsername(),
                    "deviceId=" + tokenEntity.getDeviceId() + ", reason=replay-detected"
            );
            throw new IllegalArgumentException("检测到refresh token重放，已阻断会话");
        }
        if (tokenEntity.getExpireAt().isBefore(now)) {
            throw new IllegalArgumentException("refreshToken已失效");
        }
        if (!normalizedDeviceId.equals(tokenEntity.getDeviceId())) {
            throw new IllegalArgumentException("refreshToken与设备不匹配");
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
    public Map<String, Object> logout(Jwt jwt, String refreshToken) {
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

    @Transactional(readOnly = true)
    public Map<String, Object> listActiveSessions(Jwt jwt) {
        return listActiveSessionsByUsername(jwt.getSubject());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminListUserSessions(String username) {
        logAction(ACTION_ADMIN_SESSION_QUERY, username, "operator=admin");
        return listActiveSessionsByUsername(username);
    }

    private Map<String, Object> listActiveSessionsByUsername(String username) {
        List<AuthRefreshTokenEntity> sessions =
                authRefreshTokenRepository.findByUsernameAndRevokedFalseOrderByCreatedAtDesc(username);
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (AuthRefreshTokenEntity session : sessions) {
            Map<String, Object> row = new HashMap<>();
            row.put("deviceId", session.getDeviceId());
            row.put("createdAt", session.getCreatedAt().toString());
            row.put("expireAt", session.getExpireAt().toString());
            row.put("revoked", session.getRevoked());
            items.add(row);
        }
        data.put("username", username);
        data.put("total", items.size());
        data.put("sessions", items);
        return data;
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

    @Transactional
    public Map<String, Object> revokeAllSessions(Jwt jwt) {
        Map<String, Object> result = revokeAllSessionsByUsername(jwt.getSubject());
        logAction(
                ACTION_SESSION_REVOKE_ALL,
                jwt.getSubject(),
                "revokedCount=" + result.get("revokedCount")
        );
        return result;
    }

    @Transactional
    public Map<String, Object> adminRevokeUserAllSessions(String username) {
        Map<String, Object> result = revokeAllSessionsByUsername(username);
        logAction(
                ACTION_ADMIN_SESSION_REVOKE_ALL,
                username,
                "revokedCount=" + result.get("revokedCount")
        );
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSecurityEventsSummary(int lookbackMinutes) {
        int safeLookback = lookbackMinutes <= 0 ? 60 : lookbackMinutes;
        LocalDateTime from = LocalDateTime.now().minusMinutes(safeLookback);

        Map<String, Object> data = new HashMap<>();
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
                if (created.isBefore(from)) {
                    break;
                }
                if (created.isAfter(now)) {
                    continue;
                }
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
                if (log.getCreatedAt().isBefore(from)) {
                    break;
                }
                userEventCounts.merge(log.getTargetId(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> users = userEventCounts.entrySet()
                .stream()
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
        if (deviceId == null || deviceId.isBlank()) {
            return DEFAULT_DEVICE_ID;
        }
        return deviceId.trim();
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

    @Transactional(readOnly = true)
    public Map<String, Object> buildSecurityExportPayload(
            String type,
            int lookbackMinutes,
            int topN,
            List<String> actionTypes
    ) {
        return switch (type) {
            case "timeline" -> adminSecurityEventsTimeline(lookbackMinutes, actionTypes);
            case "risk-users-top" -> adminSecurityRiskUsersTop(lookbackMinutes, topN, actionTypes);
            case "summary" -> adminSecurityEventsSummary(lookbackMinutes);
            default -> throw new IllegalArgumentException("unsupported export type");
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
            String type,
            String format,
            int lookbackMinutes,
            int topN,
            List<String> actionTypes,
            String idempotencyKey
    ) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            var existing = authExportTaskRepository.findTopByIdempotencyKeyOrderByCreatedAtDesc(normalizedIdempotencyKey);
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
            throw new IllegalArgumentException("导出任务过多，请稍后重试");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        AuthExportTaskEntity task = new AuthExportTaskEntity();
        task.setTaskId(taskId);
        task.setIdempotencyKey(normalizedIdempotencyKey);
        task.setExportType(type);
        task.setExportFormat(format);
        task.setStatus("RUNNING");
        task.setRetryCount(0);
        task.setMaxRetry(Math.max(exportTaskDefaultMaxRetry, 0));
        task.setErrorCode(ERROR_CODE_NONE);
        task.setCreatedAt(LocalDateTime.now());
        authExportTaskRepository.save(task);
        logAction(
                ACTION_EXPORT_TASK_CREATED,
                TARGET_TYPE_AUTH_EXPORT_TASK,
                taskId,
                "type=" + type + ", format=" + format + ", idempotencyKey=" + normalizedIdempotencyKey
        );
        executeExportTask(task, lookbackMinutes, topN, actionTypes);
        return getSecurityExportTask(taskId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSecurityExportTask(String taskId) {
        AuthExportTaskEntity task = authExportTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在"));
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("idempotencyKey", task.getIdempotencyKey());
        data.put("type", task.getExportType());
        data.put("format", task.getExportFormat());
        data.put("status", task.getStatus());
        data.put("retryCount", task.getRetryCount());
        data.put("maxRetry", task.getMaxRetry());
        data.put("errorCode", task.getErrorCode());
        data.put("fileName", task.getFileName());
        data.put("errorMessage", task.getErrorMessage());
        data.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
        data.put("finishedAt", task.getFinishedAt() != null ? task.getFinishedAt().toString() : null);
        return data;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSecurityExportTaskDownload(String taskId) {
        AuthExportTaskEntity task = authExportTaskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在"));
        if (!"SUCCESS".equalsIgnoreCase(task.getStatus())) {
            throw new IllegalArgumentException("导出任务尚未完成");
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
        List<Map<String, Object>> items = taskPage.getContent().stream().map(task -> {
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
        }).toList();
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
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在"));
        if ("RUNNING".equalsIgnoreCase(task.getStatus())) {
            throw new IllegalArgumentException("任务仍在运行，不能重试");
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? 0 : task.getMaxRetry();
        if (retryCount >= maxRetry) {
            throw new IllegalArgumentException("重试次数已达上限");
        }
        task.setStatus("RUNNING");
        task.setRetryCount(retryCount + 1);
        task.setErrorCode(ERROR_CODE_NONE);
        task.setErrorMessage(null);
        task.setFinishedAt(null);
        authExportTaskRepository.save(task);
        logAction(
                ACTION_EXPORT_TASK_RETRY,
                TARGET_TYPE_AUTH_EXPORT_TASK,
                taskId,
                "retryCount=" + task.getRetryCount() + ", maxRetry=" + maxRetry
        );
        executeExportTask(task, lookbackMinutes, topN, actionTypes);
        return getSecurityExportTask(taskId);
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

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
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

    private void executeExportTask(AuthExportTaskEntity task, int lookbackMinutes, int topN, List<String> actionTypes) {
        try {
            Map<String, Object> payload = buildSecurityExportPayload(
                    task.getExportType(),
                    lookbackMinutes,
                    topN,
                    actionTypes
            );
            String content = renderSecurityExportContent(task.getExportType(), task.getExportFormat(), payload);
            task.setContentText(content);
            task.setFileName("security-events-" + task.getExportType() + "." + ("csv".equalsIgnoreCase(task.getExportFormat()) ? "csv" : "json"));
            task.setStatus("SUCCESS");
            task.setErrorCode(ERROR_CODE_NONE);
            task.setFinishedAt(LocalDateTime.now());
            authExportTaskRepository.save(task);
            logAction(
                    ACTION_EXPORT_TASK_SUCCESS,
                    TARGET_TYPE_AUTH_EXPORT_TASK,
                    task.getTaskId(),
                    "type=" + task.getExportType() + ", format=" + task.getExportFormat()
            );
        } catch (RuntimeException ex) {
            task.setStatus("FAILED");
            task.setErrorCode(ERROR_CODE_EXECUTION_FAILED);
            task.setErrorMessage(ex.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            authExportTaskRepository.save(task);
            logAction(
                    ACTION_EXPORT_TASK_FAILED,
                    TARGET_TYPE_AUTH_EXPORT_TASK,
                    task.getTaskId(),
                    "error=" + ex.getMessage()
            );
        }
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
                logAction(
                        ACTION_EXPORT_TASK_TIMEOUT,
                        TARGET_TYPE_AUTH_EXPORT_TASK,
                        task.getTaskId(),
                        "createdAt=" + task.getCreatedAt()
                );
            }
        }
    }
}
