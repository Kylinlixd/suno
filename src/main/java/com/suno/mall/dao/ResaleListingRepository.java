package com.suno.mall.dao;

import com.suno.mall.entity.ResaleListingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResaleListingRepository extends JpaRepository<ResaleListingEntity, Long> {

    List<ResaleListingEntity> findByStatus(String status);
}
