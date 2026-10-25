package com.suno.mall.dao;

import com.suno.mall.entity.ResaleReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ResaleReviewRepository extends JpaRepository<ResaleReviewEntity, Long> {

    boolean existsByOrder_OrderNoAndUser_Id(String orderNo, Long userId);

    Optional<ResaleReviewEntity> findByOrder_OrderNoAndUser_Id(String orderNo, Long userId);

    Optional<ResaleReviewEntity> findByOrder_OrderNo(String orderNo);

    List<ResaleReviewEntity> findByListing_IdOrderByCreatedAtDesc(Long listingId);

    // ==================== P3 风控聚合下推 ====================

    long countByCreatedAtAfter(LocalDateTime threshold);

    long countByCreatedAtAfterAndSensitiveHitTrue(LocalDateTime threshold);

    /** 按商品分组：评价数与敏感数（topListings 用） */
    @Query("""
            SELECT r.listing.id, count(r), sum(CASE WHEN r.sensitiveHit = true THEN 1 ELSE 0 END)
            FROM ResaleReviewEntity r
            WHERE r.createdAt >= :threshold
            GROUP BY r.listing.id
            """)
    List<Object[]> groupByListingSince(@Param("threshold") LocalDateTime threshold);
}
