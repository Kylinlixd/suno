package com.recycle.mall.dao;

import com.recycle.mall.entity.RecycleOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecycleOrderRepository extends JpaRepository<RecycleOrderEntity, Long> {

    Optional<RecycleOrderEntity> findByOrderNo(String orderNo);
}
