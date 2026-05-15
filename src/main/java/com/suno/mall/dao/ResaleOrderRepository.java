package com.suno.mall.dao;

import com.suno.mall.entity.ResaleOrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 使用 JOIN FETCH 按订单号查询，避免 N+1 问题
     */
    @Query("SELECT o FROM ResaleOrderEntity o LEFT JOIN FETCH o.buyerUser LEFT JOIN FETCH o.listing l LEFT JOIN FETCH l.product WHERE o.orderNo = :orderNo")
    Optional<ResaleOrderEntity> findWithDetailsByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 使用 JOIN FETCH 查询买家订单列表，避免 N+1 问题
     */
    @Query("SELECT o FROM ResaleOrderEntity o LEFT JOIN FETCH o.listing l LEFT JOIN FETCH l.product WHERE o.buyerUser.id = :buyerUserId ORDER BY o.createdAt DESC")
    List<ResaleOrderEntity> findByBuyerIdWithDetails(@Param("buyerUserId") Long buyerUserId);

    /**
     * 使用 JOIN FETCH 按支付状态和履约状态查询，避免 N+1 问题
     */
    @Query("SELECT o FROM ResaleOrderEntity o LEFT JOIN FETCH o.buyerUser LEFT JOIN FETCH o.listing l LEFT JOIN FETCH l.product WHERE o.payStatus = :payStatus AND o.fulfillStatus = :fulfillStatus")
    List<ResaleOrderEntity> findByPayStatusAndFulfillStatusWithDetails(@Param("payStatus") String payStatus, @Param("fulfillStatus") String fulfillStatus);
}
