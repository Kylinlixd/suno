package com.suno.mall.dao;

import com.suno.mall.entity.EventOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findByStatusOrderByIdAsc(String status, Pageable pageable);

    boolean existsByUseCaseIdAndPayload(String useCaseId, String payload);

    /** 派发成功后标记（至少一次投递：失败保持 PENDING 等下轮） */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE EventOutboxEntity e SET e.status = 'PUBLISHED', e.publishedAt = :now WHERE e.id = :id AND e.status = 'PENDING'")
    int markPublished(@Param("id") Long id, @Param("now") LocalDateTime now);
}
