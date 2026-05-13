package com.suno.mall.dao;

import com.recycle.mall.entity.OperationAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLogEntity, Long>,
        JpaSpecificationExecutor<OperationAuditLogEntity> {

    List<OperationAuditLogEntity> findByActionTypeOrderByCreatedAtDesc(String actionType);

    List<OperationAuditLogEntity> findByTargetIdOrderByCreatedAtDesc(String targetId);

    List<OperationAuditLogEntity> findByActionTypeAndTargetIdOrderByCreatedAtDesc(String actionType, String targetId);

    Page<OperationAuditLogEntity> findByActionType(String actionType, Pageable pageable);
}
