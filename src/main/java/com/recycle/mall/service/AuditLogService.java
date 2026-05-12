
package com.recycle.mall.service;

import org.jspecify.annotations.Nullable;
import com.recycle.mall.service.support.AuditContext;
import com.recycle.mall.service.support.AuditLogHelper;
import com.recycle.mall.entity.OperationAuditLogEntity;
import com.recycle.mall.dao.OperationAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务
 */
@Service
public class AuditLogService {

    private final OperationAuditLogRepository operationAuditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(
            OperationAuditLogRepository operationAuditLogRepository,
            ObjectMapper objectMapper
    ) {
        this.operationAuditLogRepository = operationAuditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logAction(String actionType, String targetType, String targetId, String detail) {
        OperationAuditLogEntity log = new OperationAuditLogEntity();
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        operationAuditLogRepository.save(log);
    }

    public void logExternalQueryAction(String actionType, String targetId, String detail) {
        logAction(actionType, "PAYMENT_REPLAY_QUEUE", targetId, detail);
    }

    public String buildAuditContextSuffix(@Nullable AuditContext auditContext) {
        return AuditLogHelper.buildAuditContextSuffix(auditContext, objectMapper);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAuditLogs(@Nullable String actionType, @Nullable String targetId, int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        Page<OperationAuditLogEntity> page = operationAuditLogRepository.findAll(
                buildAuditLogSpec(actionType, targetId),
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return page.getContent().stream().map(log -> Map.<String, Object>ofEntries(
                Map.entry("id", log.getId()),
                Map.entry("actionType", log.getActionType()),
                Map.entry("targetType", log.getTargetType()),
                Map.entry("targetId", log.getTargetId()),
                Map.entry("detail", log.getDetail()),
                Map.entry("createdAt", log.getCreatedAt())
        )).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pageAuditLogs(@Nullable String actionType, @Nullable String targetId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        Page<OperationAuditLogEntity> result = operationAuditLogRepository.findAll(
                buildAuditLogSpec(actionType, targetId),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return Map.of(
                "page", safePage,
                "size", safeSize,
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "items", result.getContent().stream().map(log -> Map.of(
                        "id", log.getId(),
                        "actionType", log.getActionType(),
                        "targetType", log.getTargetType(),
                        "targetId", log.getTargetId(),
                        "detail", log.getDetail(),
                        "createdAt", log.getCreatedAt()
                )).toList()
        );
    }

    @Transactional(readOnly = true)
    public String exportAuditLogsCsv(@Nullable String actionType, @Nullable String targetId, int limit) {
        int safeLimit = Math.max(1, Math.min(5000, limit));
        Page<OperationAuditLogEntity> page = operationAuditLogRepository.findAll(
                buildAuditLogSpec(actionType, targetId),
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        StringBuilder csv = new StringBuilder();
        csv.append("id,actionType,targetType,targetId,detail,createdAt\n");
        for (OperationAuditLogEntity log : page.getContent()) {
            csv.append(log.getId()).append(",")
                    .append(csvEscape(log.getActionType())).append(",")
                    .append(csvEscape(log.getTargetType())).append(",")
                    .append(csvEscape(log.getTargetId())).append(",")
                    .append(csvEscape(log.getDetail())).append(",")
                    .append(csvEscape(String.valueOf(log.getCreatedAt())))
                    .append("\n");
        }
        return csv.toString();
    }

    @Nullable
    public LocalDateTime resolveOrderCompletedAt(String orderNo) {
        return operationAuditLogRepository.findByTargetIdOrderByCreatedAtDesc(orderNo).stream()
                .filter(item -> "RESALE_ORDER".equals(item.getTargetType()))
                .filter(item -> "RESALE_ORDER_RECEIVE".equals(item.getActionType())
                        || "RESALE_ORDER_AUTO_RECEIVE".equals(item.getActionType()))
                .map(OperationAuditLogEntity::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    public boolean isDeliveredBefore(String orderNo, LocalDateTime threshold) {
        return operationAuditLogRepository.findByActionTypeAndTargetIdOrderByCreatedAtDesc(
                        "RESALE_ORDER_DELIVER",
                        orderNo
                ).stream()
                .findFirst()
                .map(log -> !log.getCreatedAt().isAfter(threshold))
                .orElse(false);
    }

    private org.springframework.data.jpa.domain.Specification<OperationAuditLogEntity> buildAuditLogSpec(
            @Nullable String actionType, @Nullable String targetId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(cb.equal(root.get("actionType"), actionType));
            }
            if (targetId != null && !targetId.isBlank()) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String csvEscape(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String escaped = raw.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
