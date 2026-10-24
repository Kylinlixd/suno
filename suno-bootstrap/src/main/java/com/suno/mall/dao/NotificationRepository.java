package com.suno.mall.dao;

import com.suno.mall.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    Page<NotificationEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    int countByUserIdAndIsReadFalse(Long userId);

    Optional<NotificationEntity> findByIdAndUserId(Long id, Long userId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.id = :id AND n.userId = :userId AND n.isRead = false")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllRead(@Param("userId") Long userId);
}
