package com.suno.mall.dao;

import com.suno.mall.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {

    List<ProductImageEntity> findByProduct_IdOrderBySortOrderAscIdAsc(Long productId);

    void deleteByProduct_Id(Long productId);
}
