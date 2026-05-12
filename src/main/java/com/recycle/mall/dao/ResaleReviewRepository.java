package com.recycle.mall.dao;

import com.recycle.mall.entity.ResaleReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResaleReviewRepository extends JpaRepository<ResaleReviewEntity, Long> {

    boolean existsByOrder_OrderNoAndUser_Id(String orderNo, Long userId);

    Optional<ResaleReviewEntity> findByOrder_OrderNoAndUser_Id(String orderNo, Long userId);

    Optional<ResaleReviewEntity> findByOrder_OrderNo(String orderNo);

    List<ResaleReviewEntity> findByListing_IdOrderByCreatedAtDesc(Long listingId);
}
