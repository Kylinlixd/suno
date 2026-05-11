package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.ResaleReviewReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResaleReviewReportRepository extends JpaRepository<ResaleReviewReportEntity, Long> {

    boolean existsByReview_IdAndReporterUser_Id(Long reviewId, Long reporterUserId);

    long countByReview_Id(Long reviewId);

    List<ResaleReviewReportEntity> findByStatusOrderByCreatedAtDesc(String status);
}
