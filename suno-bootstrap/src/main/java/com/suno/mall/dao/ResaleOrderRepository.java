package com.suno.mall.dao;

import com.suno.mall.entity.ResaleOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 买家订单分页查询。空字符串表示不过滤，避免 Hibernate 对可空参数推断类型失败。
     */
    @Query(value = """
            SELECT o FROM ResaleOrderEntity o
            JOIN FETCH o.listing l
            JOIN FETCH l.product
            WHERE o.buyerUser.id = :buyerUserId
              AND (:payStatus = '' OR o.payStatus = :payStatus)
              AND (:fulfillStatus = '' OR o.fulfillStatus = :fulfillStatus)
            """,
            countQuery = """
            SELECT COUNT(o) FROM ResaleOrderEntity o
            WHERE o.buyerUser.id = :buyerUserId
              AND (:payStatus = '' OR o.payStatus = :payStatus)
              AND (:fulfillStatus = '' OR o.fulfillStatus = :fulfillStatus)
            """)
    Page<ResaleOrderEntity> pageByBuyer(
            @Param("buyerUserId") Long buyerUserId,
            @Param("payStatus") String payStatus,
            @Param("fulfillStatus") String fulfillStatus,
            Pageable pageable
    );

    /**
     * 条件更新状态（CAS）。返回 1 表示本次转换成功，0 表示订单状态已被其它事务改变。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ResaleOrderEntity o
            SET o.payStatus = :newPayStatus, o.fulfillStatus = :newFulfillStatus
            WHERE o.orderNo = :orderNo
              AND o.payStatus = :expectedPayStatus
              AND o.fulfillStatus = :expectedFulfillStatus
            """)
    int compareAndSetStatus(
            @Param("orderNo") String orderNo,
            @Param("expectedPayStatus") String expectedPayStatus,
            @Param("expectedFulfillStatus") String expectedFulfillStatus,
            @Param("newPayStatus") String newPayStatus,
            @Param("newFulfillStatus") String newFulfillStatus
    );

    /**
     * 仅 PAID 订单可退款；并发退款只有一个事务能成功。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ResaleOrderEntity o
            SET o.payStatus = 'REFUNDED', o.fulfillStatus = 'REFUNDED'
            WHERE o.orderNo = :orderNo AND o.payStatus = 'PAID'
            """)
    int refundIfPaid(@Param("orderNo") String orderNo);
}
