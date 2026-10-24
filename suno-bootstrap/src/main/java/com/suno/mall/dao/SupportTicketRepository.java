package com.suno.mall.dao;

import com.suno.mall.entity.SupportTicketEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, Long> {

    Page<SupportTicketEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
