package com.suno.mall.dao;

import com.suno.mall.entity.RecycleOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecycleOrderRepository extends JpaRepository<RecycleOrderEntity, Long> {

    Optional<RecycleOrderEntity> findByOrderNo(String orderNo);

    /**
     * 使用 JOIN FETCH 优化关联查询，避免 N+1 问题
     */
    @Query("SELECT r FROM RecycleOrderEntity r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.product WHERE r.orderNo = :orderNo")
    Optional<RecycleOrderEntity> findWithDetailsByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 使用 JOIN FETCH 批量查询，避免 N+1 问题
     */
    @Query("SELECT r FROM RecycleOrderEntity r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.product")
    List<RecycleOrderEntity> findAllWithDetails();

    /**
     * 按状态查询，使用 JOIN FETCH 优化
     */
    @Query("SELECT r FROM RecycleOrderEntity r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.product WHERE r.status = :status")
    List<RecycleOrderEntity> findByStatusWithDetails(@Param("status") String status);
}
