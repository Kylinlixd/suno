package com.suno.mall.dao;

import com.suno.mall.entity.ResaleListingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ResaleListingRepository extends JpaRepository<ResaleListingEntity, Long> {

    List<ResaleListingEntity> findByStatus(String status);

    /**
     * 使用 JOIN FETCH 按状态查询，避免 N+1 问题
     */
    @Query("SELECT l FROM ResaleListingEntity l LEFT JOIN FETCH l.product LEFT JOIN FETCH l.recycleOrder WHERE l.status = :status")
    List<ResaleListingEntity> findByStatusWithDetails(@Param("status") String status);

    /**
     * 使用 JOIN FETCH 按 ID 查询，避免 N+1 问题
     */
    @Query("SELECT l FROM ResaleListingEntity l LEFT JOIN FETCH l.product LEFT JOIN FETCH l.recycleOrder WHERE l.id = :id")
    Optional<ResaleListingEntity> findWithDetailsById(@Param("id") Long id);

    /**
     * 使用 JOIN FETCH 查询所有上架商品，避免 N+1 问题
     */
    @Query("SELECT l FROM ResaleListingEntity l LEFT JOIN FETCH l.product LEFT JOIN FETCH l.recycleOrder")
    List<ResaleListingEntity> findAllWithDetails();

    /** M3 管理端：全量商品分页（含商品详情） */
    @Query("SELECT l FROM ResaleListingEntity l LEFT JOIN FETCH l.product LEFT JOIN FETCH l.recycleOrder")
    org.springframework.data.domain.Page<ResaleListingEntity> findAllWithDetails(org.springframework.data.domain.Pageable pageable);

    /** M3 管理端：按状态分页（含商品详情） */
    @Query("SELECT l FROM ResaleListingEntity l LEFT JOIN FETCH l.product LEFT JOIN FETCH l.recycleOrder WHERE l.status = :status")
    org.springframework.data.domain.Page<ResaleListingEntity> findByStatusWithDetailsPaged(@Param("status") String status, org.springframework.data.domain.Pageable pageable);

    /**
     * 原子预留一件库存，库存不足或已下架时返回 0。
     * 同步递增 version，避免与仍使用实体乐观锁的库存操作互相覆盖。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ResaleListingEntity l
            SET l.stock = l.stock - 1,
                l.status = CASE WHEN l.stock - 1 = 0 THEN :soldOutStatus ELSE l.status END,
                l.updatedAt = :now,
                l.version = l.version + 1
            WHERE l.id = :listingId AND l.status = :onShelfStatus AND l.stock > 0
            """)
    int reserveOneStock(
            @Param("listingId") Long listingId,
            @Param("onShelfStatus") String onShelfStatus,
            @Param("soldOutStatus") String soldOutStatus,
            @Param("now") LocalDateTime now
    );

    /**
     * 原子归还一件库存。订单状态 CAS 成功后调用，保证同一订单只释放一次。
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ResaleListingEntity l
            SET l.stock = l.stock + 1,
                l.status = CASE WHEN l.status = :soldOutStatus THEN :onShelfStatus ELSE l.status END,
                l.updatedAt = :now,
                l.version = l.version + 1
            WHERE l.id = :listingId
            """)
    int releaseOneStock(
            @Param("listingId") Long listingId,
            @Param("onShelfStatus") String onShelfStatus,
            @Param("soldOutStatus") String soldOutStatus,
            @Param("now") LocalDateTime now
    );
}
