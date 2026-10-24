package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.config.AuthTransactional;
import com.suno.mall.entity.UserAccountEntity;
import com.suno.mall.dao.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * M3 运营后台：用户管理。封禁同时撤销该用户全部会话，解封不自动恢复会话。
 */
@Service
public class AdminUserService {

    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "BANNED");

    private final UserAccountRepository userAccountRepository;
    private final AuthSessionService authSessionService;
    private final AuditLogService auditLogService;

    public AdminUserService(UserAccountRepository userAccountRepository,
                            AuthSessionService authSessionService,
                            AuditLogService auditLogService) {
        this.userAccountRepository = userAccountRepository;
        this.authSessionService = authSessionService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> pageUsers(String keyword, String status, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Specification<UserAccountEntity> spec = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("username")), like));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("accountStatus"), status));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<UserAccountEntity> result = userAccountRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        var content = result.getContent().stream().map(user -> {
            Map<String, Object> row = new HashMap<>();
            row.put("userId", user.getId());
            row.put("username", user.getUsername());
            row.put("role", user.getRoleCode());
            row.put("accountStatus", user.getAccountStatus());
            row.put("level", user.getLevel());
            row.put("points", user.getPoints());
            return row;
        }).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("totalElements", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        payload.put("page", safePage);
        payload.put("size", safeSize);
        return payload;
    }

    @AuthTransactional
    public Map<String, Object> setUserStatus(Long userId, String accountStatus) {
        String normalized = accountStatus == null ? "" : accountStatus.trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new BizException("仅支持 ACTIVE/BANNED", ErrorCode.PARAM_INVALID);
        }
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在: " + userId, ErrorCode.PARAM_INVALID));
        if ("ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            throw new BizException("不允许封禁管理员账户", ErrorCode.AUTH_FORBIDDEN);
        }
        user.setAccountStatus(normalized);
        userAccountRepository.save(user);
        if ("BANNED".equals(normalized)) {
            authSessionService.adminRevokeUserAllSessions(user.getUsername());
        }
        auditLogService.logAction("ADMIN_USER_STATUS", "USER_ACCOUNT", String.valueOf(userId), "status=" + normalized);
        return Map.of("userId", userId, "accountStatus", normalized);
    }
}
