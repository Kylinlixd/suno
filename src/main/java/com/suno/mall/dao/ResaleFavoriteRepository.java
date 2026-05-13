package com.suno.mall.dao;

import com.recycle.mall.entity.ResaleFavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResaleFavoriteRepository extends JpaRepository<ResaleFavoriteEntity, Long> {

    Optional<ResaleFavoriteEntity> findByUser_IdAndListing_Id(Long userId, Long listingId);

    List<ResaleFavoriteEntity> findByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByListing_Id(Long listingId);
}
