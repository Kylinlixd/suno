package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.ResaleListingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResaleListingRepository extends JpaRepository<ResaleListingEntity, Long> {

    List<ResaleListingEntity> findByStatus(String status);
}
