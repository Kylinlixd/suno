package com.suno.mall.dao;

import com.suno.mall.entity.PaymentCallbackLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCallbackLogRepository extends JpaRepository<PaymentCallbackLogEntity, Long> {
    java.util.List<com.suno.mall.entity.PaymentCallbackLogEntity> findByCreatedAtAfter(java.time.LocalDateTime after);

    Page<PaymentCallbackLogEntity> findByCallbackStatus(String callbackStatus, Pageable pageable);
}
