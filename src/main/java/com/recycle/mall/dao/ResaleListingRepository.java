package com.recycle.mall.dao;

import com.recycle.mall.entity.ResaleListingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResaleListingRepository extends JpaRepository<ResaleListingEntity, Long> {

    List<ResaleListingEntity> findByStatus(String status);
}
