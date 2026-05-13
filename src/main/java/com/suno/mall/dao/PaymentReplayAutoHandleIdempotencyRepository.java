package com.suno.mall.dao;

import com.recycle.mall.entity.PaymentReplayAutoHandleIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentReplayAutoHandleIdempotencyRepository
        extends JpaRepository<PaymentReplayAutoHandleIdempotencyEntity, Long>,
        JpaSpecificationExecutor<PaymentReplayAutoHandleIdempotencyEntity> {

    Optional<PaymentReplayAutoHandleIdempotencyEntity> findByTraceId(String traceId);

    long deleteByExpireAtBefore(LocalDateTime expireAt);

    long deleteByTraceId(String traceId);

    long deleteByCreatedAtBefore(LocalDateTime createdAt);
}
