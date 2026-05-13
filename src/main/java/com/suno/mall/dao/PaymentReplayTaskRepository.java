package com.suno.mall.dao;

import com.recycle.mall.entity.PaymentReplayTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentReplayTaskRepository extends JpaRepository<PaymentReplayTaskEntity, Long> {

    List<PaymentReplayTaskEntity> findByStatusOrderByCreatedAtAsc(String status);

    List<PaymentReplayTaskEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    List<PaymentReplayTaskEntity> findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
            String status,
            java.time.LocalDateTime nextRetryAt,
            Pageable pageable
    );

    Page<PaymentReplayTaskEntity> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    long countByStatusAndNextRetryAtBefore(String status, LocalDateTime nextRetryAt);

    Optional<PaymentReplayTaskEntity> findFirstByStatusOrderByCreatedAtAsc(String status);

    Optional<PaymentReplayTaskEntity> findFirstByCallbackLogIdAndStatusInOrderByCreatedAtDesc(
            Long callbackLogId,
            List<String> statuses
    );
}
