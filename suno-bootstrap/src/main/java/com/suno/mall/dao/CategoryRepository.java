package com.suno.mall.dao;

import com.suno.mall.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findByStatusOrderBySortOrderAscIdAsc(String status);

    Optional<CategoryEntity> findByNameAndStatus(String name, String status);

    Optional<CategoryEntity> findFirstByStatusOrderBySortOrderAscIdAsc(String status);
}
