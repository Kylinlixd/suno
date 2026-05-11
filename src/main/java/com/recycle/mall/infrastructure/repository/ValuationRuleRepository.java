package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.ValuationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValuationRuleRepository extends JpaRepository<ValuationRuleEntity, Long> {

    List<ValuationRuleEntity> findByBrandInAndModelIn(List<String> brands, List<String> models);
}
