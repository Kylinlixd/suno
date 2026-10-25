package com.suno.mall.dao;

import com.suno.mall.entity.RefundOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundOrderRepository extends JpaRepository<RefundOrderEntity, Long> {

    Optional<RefundOrderEntity> findByIdAndBuyerUserId(Long id, Long buyerUserId);

    Page<RefundOrderEntity> findByBuyerUserIdOrderByCreatedAtDesc(Long buyerUserId, Pageable pageable);

    Optional<RefundOrderEntity> findFirstByResaleOrderIdAndStatusOrderByCreatedAtDesc(Long resaleOrderId, String status);

    org.springframework.data.domain.Page<RefundOrderEntity> findByStatusOrderByCreatedAtDesc(String status, org.springframework.data.domain.Pageable pageable);

    boolean existsByResaleOrderIdAndStatusIn(Long resaleOrderId, java.util.Collection<String> statuses);
}
