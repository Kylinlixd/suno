package com.suno.mall.dao;

import com.suno.mall.entity.CouponUserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponUserRepository extends JpaRepository<CouponUserEntity, Long> {

    Optional<CouponUserEntity> findByIdAndUser_Id(Long id, Long userId);

    int countByUser_IdAndCoupon_Id(Long userId, Long couponId);

    /** 我的券：按状态过滤，未用先展示且按过期时间升序 */
    @Query("""
            SELECT cu FROM CouponUserEntity cu LEFT JOIN FETCH cu.coupon
            WHERE cu.user.id = :userId AND (:status IS NULL OR cu.status = :status)
            """)
    Page<CouponUserEntity> pageMine(@Param("userId") Long userId, @Param("status") String status, Pageable pageable);

    Optional<CouponUserEntity> findByOrderId(Long orderId);

    /** 幂等核销：仅 UNUSED 且未过期的本人券可被标记 USED */
    @Query("""
            SELECT COUNT(cu) FROM CouponUserEntity cu
            WHERE cu.id = :id AND cu.user.id = :userId AND cu.status = 'UNUSED' AND cu.expireAt > :now
            """)
    int countRedeemable(@Param("id") Long id, @Param("userId") Long userId, @Param("now") java.time.LocalDateTime now);
}
