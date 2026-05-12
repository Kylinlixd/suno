package com.recycle.mall.dao;

import com.recycle.mall.entity.ResaleReviewReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResaleReviewReportRepository extends JpaRepository<ResaleReviewReportEntity, Long> {

    boolean existsByReview_IdAndReporterUser_Id(Long reviewId, Long reporterUserId);

    long countByReview_Id(Long reviewId);

    List<ResaleReviewReportEntity> findByStatusOrderByCreatedAtDesc(String status);
}
