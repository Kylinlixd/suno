package com.suno.mall.dao;

import com.suno.mall.entity.RecycleOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecycleOrderRepository extends JpaRepository<RecycleOrderEntity, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<RecycleOrderEntity> {

    Optional<RecycleOrderEntity> findByOrderNo(String orderNo);

    /**
     * M1 用户回收历史：按用户分页查询并预取商品，避免 N+1。
     */
    @Query("SELECT r FROM RecycleOrderEntity r LEFT JOIN FETCH r.product WHERE r.user.id = :userId")
    Page<RecycleOrderEntity> findPageByUser_Id(@Param("userId") Long userId, Pageable pageable);

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
