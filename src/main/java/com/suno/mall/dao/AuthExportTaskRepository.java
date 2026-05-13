package com.suno.mall.dao;

import com.suno.mall.entity.AuthExportTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthExportTaskRepository extends JpaRepository<AuthExportTaskEntity, Long> {
    Optional<AuthExportTaskEntity> findByTaskId(String taskId);
    Optional<AuthExportTaskEntity> findTopByIdempotencyKeyOrderByCreatedAtDesc(String idempotencyKey);

    Page<AuthExportTaskEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<AuthExportTaskEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    long countByStatus(String status);

    List<AuthExportTaskEntity> findByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("delete from AuthExportTaskEntity t where t.finishedAt is not null and t.finishedAt < :cutoff")
    int deleteFinishedTasksBefore(LocalDateTime cutoff);
}
