package com.suno.mall.controller;

import com.suno.mall.dao.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * OPS-S003 通知保留期清理：批量删除保留期（默认 90 天）外的已读通知，
 * 防止 suno_notification 无界增长；未读通知永不清理。
 */
@Component
public class NotificationCleanupScheduler {

    private final NotificationRepository notificationRepository;

    public NotificationCleanupScheduler(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(fixedDelayString = "${operations.notification.cleanup-fixed-delay-ms:86400000}")
    @Transactional
    public int cleanupExpiredReadNotifications() {
        int retentionDays = 90;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return notificationRepository.deleteExpiredRead(cutoff);
    }
}
