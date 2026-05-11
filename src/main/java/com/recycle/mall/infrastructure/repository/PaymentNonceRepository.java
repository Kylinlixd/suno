package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.PaymentNonceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentNonceRepository extends JpaRepository<PaymentNonceEntity, Long> {

    Optional<PaymentNonceEntity> findByNonce(String nonce);

    void deleteByExpireAtBefore(LocalDateTime time);
}
