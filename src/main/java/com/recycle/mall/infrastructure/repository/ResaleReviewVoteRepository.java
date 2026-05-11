package com.recycle.mall.infrastructure.repository;

import com.recycle.mall.infrastructure.entity.ResaleReviewVoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResaleReviewVoteRepository extends JpaRepository<ResaleReviewVoteEntity, Long> {

    boolean existsByReview_IdAndUser_Id(Long reviewId, Long userId);

    long countByReview_Id(Long reviewId);
}
