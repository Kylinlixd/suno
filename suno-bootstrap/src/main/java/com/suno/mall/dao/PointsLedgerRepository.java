package com.suno.mall.dao;

import com.suno.mall.entity.PointsLedgerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointsLedgerRepository extends JpaRepository<PointsLedgerEntity, Long> {

    /**
     * M1 积分流水：按用户分页，最新在前。
     */
    @Query("SELECT p FROM PointsLedgerEntity p WHERE p.user.id = :userId ORDER BY p.createdAt DESC, p.id DESC")
    Page<PointsLedgerEntity> findPageByUser_Id(@Param("userId") Long userId, Pageable pageable);
}
