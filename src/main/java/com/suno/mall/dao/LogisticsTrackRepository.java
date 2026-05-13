package com.suno.mall.dao;

import com.suno.mall.entity.LogisticsTrackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LogisticsTrackRepository extends JpaRepository<LogisticsTrackEntity, Long> {

    Optional<LogisticsTrackEntity> findByTrackingNo(String trackingNo);
}
