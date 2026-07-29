package com.suno.mall.dao;

import com.suno.mall.entity.ResaleListingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
