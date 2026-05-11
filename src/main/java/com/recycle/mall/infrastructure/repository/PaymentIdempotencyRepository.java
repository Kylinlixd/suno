package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.PaymentIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyEntity, Long> {

    Optional<PaymentIdempotencyEntity> findByIdempotencyKey(String idempotencyKey);
}
