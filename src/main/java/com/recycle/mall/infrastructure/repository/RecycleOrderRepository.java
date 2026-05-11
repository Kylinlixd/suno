package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.RecycleOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecycleOrderRepository extends JpaRepository<RecycleOrderEntity, Long> {

    Optional<RecycleOrderEntity> findByOrderNo(String orderNo);
}
