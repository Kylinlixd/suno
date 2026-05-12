package com.recycle.mall.dao;

import com.recycle.mall.entity.PaymentCallbackLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCallbackLogRepository extends JpaRepository<PaymentCallbackLogEntity, Long> {

    Page<PaymentCallbackLogEntity> findByCallbackStatus(String callbackStatus, Pageable pageable);
}
