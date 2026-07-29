package com.suno.mall.dao;

import com.suno.mall.entity.PointsLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointsLedgerRepository extends JpaRepository<PointsLedgerEntity, Long> {
}
