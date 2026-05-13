package com.suno.mall.dao;

import com.suno.mall.entity.ResaleOrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ResaleOrderRepository extends JpaRepository<ResaleOrderEntity, Long> {

    Optional<ResaleOrderEntity> findByOrderNo(String orderNo);

    List<ResaleOrderEntity> findByPayStatusAndFulfillStatusAndCreatedAtBefore(
            String payStatus,
            String fulfillStatus,
            LocalDateTime createdAt
    );

    List<ResaleOrderEntity> findByPayStatusAndFulfillStatusAndCreatedAtBefore(
            String payStatus,
            String fulfillStatus,
            LocalDateTime createdAt,
            Pageable pageable
    );

    List<ResaleOrderEntity> findByPayStatusAndFulfillStatus(
            String payStatus,
            String fulfillStatus,
            Pageable pageable
    );

    List<ResaleOrderEntity> findByBuyerUser_IdOrderByCreatedAtDesc(Long buyerUserId);
}
