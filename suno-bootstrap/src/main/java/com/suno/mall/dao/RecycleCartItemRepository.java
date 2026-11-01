package com.suno.mall.dao;

import com.suno.mall.entity.RecycleCartItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecycleCartItemRepository extends JpaRepository<RecycleCartItem, Long> {

    List<RecycleCartItem> findByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    Optional<RecycleCartItem> findByIdAndUser_Id(Long id, Long userId);

    Page<RecycleCartItem> findByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, String status, Pageable pageable);
}
