package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.PointsLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointsLedgerRepository extends JpaRepository<PointsLedgerEntity, Long> {
}
