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

public interface ResaleOrderRepository extends JpaRepository<ResaleOrderEntity, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ResaleOrderEntity> {

    /**
     * M3 管理端订单分页：可选 pay/fulfill 过滤与订单号/买家用户名关键词。
     */
    @Query("""
            SELECT o FROM ResaleOrderEntity o
            LEFT JOIN FETCH o.buyerUser LEFT JOIN FETCH o.listing
            WHERE (:payStatus IS NULL OR o.payStatus = :payStatus)
              AND (:fulfillStatus IS NULL OR o.fulfillStatus = :fulfillStatus)
              AND (:keyword IS NULL OR LOWER(o.orderNo) LIKE :keyword OR LOWER(o.buyerUser.username) LIKE :keyword)
            """)
    org.springframework.data.domain.Page<ResaleOrderEntity> adminPage(
            @Param("payStatus") String payStatus,
            @Param("fulfillStatus") String fulfillStatus,
            @Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);

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

    // ==================== P3 报表聚合下推（窗口 [start, end)，end null 开区间） ====================

    @Query("""
            SELECT count(o) FROM ResaleOrderEntity o
            WHERE o.createdAt >= :start AND (:end IS NULL OR o.createdAt < :end)
            """)
    long countInWindow(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("""
            SELECT count(o) FROM ResaleOrderEntity o
            WHERE o.createdAt >= :start AND (:end IS NULL OR o.createdAt < :end) AND o.payStatus IN :statuses
            """)
    long countByPayStatusInWindow(@Param("statuses") java.util.Collection<String> statuses,
                                  @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("""
            SELECT count(o) FROM ResaleOrderEntity o
            WHERE o.createdAt >= :start AND (:end IS NULL OR o.createdAt < :end) AND o.fulfillStatus = :status
            """)
    long countByFulfillStatusInWindow(@Param("status") String status,
                                      @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("""
            SELECT coalesce(sum(o.payableAmount), 0) FROM ResaleOrderEntity o
            WHERE o.createdAt >= :start AND (:end IS NULL OR o.createdAt < :end) AND o.payStatus IN :statuses
            """)
    java.math.BigDecimal sumPayableInWindow(@Param("statuses") java.util.Collection<String> statuses,
                                            @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("""
            SELECT count(DISTINCT o.buyerUser.id) FROM ResaleOrderEntity o
            WHERE o.createdAt >= :start AND (:end IS NULL OR o.createdAt < :end)
            """)
    long countDistinctBuyersInWindow(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    /** 按日历日分组：订单数与支付数（H2/MySQL 均支持 function('date', ...)） */
    @Query("""
            SELECT function('date', o.createdAt), count(o),
                   sum(CASE WHEN o.payStatus IN ('PAID', 'REFUNDED') THEN 1 ELSE 0 END)
            FROM ResaleOrderEntity o
            WHERE o.createdAt >= :start
            GROUP BY function('date', o.createdAt)
            """)
    java.util.List<Object[]> dailySeries(@Param("start") java.time.LocalDateTime start);

    /**
     * M3 支付渠道回填：仅 PAID 订单可回填，幂等（重复通知覆盖同一渠道信息）。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ResaleOrderEntity o
            SET o.payChannel = :payChannel, o.tradeNo = :tradeNo, o.paidAt = :paidAt
            WHERE o.orderNo = :orderNo AND o.payStatus = 'PAID'
            """)
    int attachChannelInfo(@Param("orderNo") String orderNo, @Param("payChannel") String payChannel,
                          @Param("tradeNo") String tradeNo, @Param("paidAt") java.time.LocalDateTime paidAt);
}
