package com.suno.mall.dao;

import com.suno.mall.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {

    Optional<CartItemEntity> findByIdAndUser_Id(Long id, Long userId);

    Optional<CartItemEntity> findByUser_IdAndListing_Id(Long userId, Long listingId);

    void deleteByUser_IdAndListing_Id(Long userId, Long listingId);

    @Query("SELECT c FROM CartItemEntity c LEFT JOIN FETCH c.listing l LEFT JOIN FETCH l.product WHERE c.user.id = :userId ORDER BY c.createdAt DESC, c.id DESC")
    List<CartItemEntity> findWithDetailsByUser_Id(@Param("userId") Long userId);
}
