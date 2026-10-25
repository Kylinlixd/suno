package com.suno.mall.dao;

import com.suno.mall.entity.CouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepository extends JpaRepository<CouponEntity, Long> {

    List<CouponEntity> findByStatusAndEndTimeAfterOrderByEndTimeAsc(String status, LocalDateTime now);

    org.springframework.data.domain.Page<CouponEntity> findByStatus(String status, org.springframework.data.domain.Pageable pageable);

    /** 原子扣减一张券的库存；不足或停用返回 0，配合 per-user 领取上限使用 */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE CouponEntity c
            SET c.remainingCount = c.remainingCount - 1
            WHERE c.id = :couponId AND c.status = :status AND c.remainingCount > 0
            """)
    int consumeOneStock(@Param("couponId") Long couponId, @Param("status") String status);

    /** 管理端停用/库存回补时使用 */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE CouponEntity c SET c.remainingCount = c.remainingCount + 1 WHERE c.id = :couponId")
    int restoreOneStock(@Param("couponId") Long couponId);
}
