package com.suno.mall.dao;

import com.suno.mall.entity.PaymentIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyEntity, Long> {

    Optional<PaymentIdempotencyEntity> findByIdempotencyKey(String idempotencyKey);
}
