package com.recycle.mall.dao;

import com.recycle.mall.entity.ResaleReviewVoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResaleReviewVoteRepository extends JpaRepository<ResaleReviewVoteEntity, Long> {

    boolean existsByReview_IdAndUser_Id(Long reviewId, Long userId);

    long countByReview_Id(Long reviewId);
}
