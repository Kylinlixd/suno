package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.entity.NotificationEntity;
import com.suno.mall.dao.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M2 站内通知。当前实现为同步落库（调用方事务内）；
 * 事件化（outbox → 通知消费者）是 M4 的目标形态。
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SmsNotificationChannel smsNotificationChannel;

    public NotificationService(NotificationRepository notificationRepository,
                               SmsNotificationChannel smsNotificationChannel) {
        this.notificationRepository = notificationRepository;
        this.smsNotificationChannel = smsNotificationChannel;
    }

    /** 领域事件入口：调用方事务内写入通知事实；关键事件（退款/发货）可选同步短信 */
    public void push(Long userId, String notificationType, String title, String content,
                     Long relatedId, String relatedType) {
        push(userId, notificationType, title, content, relatedId, relatedType, null);
    }

    public void push(Long userId, String notificationType, String title, String content,
                     Long relatedId, String relatedType, String username) {
        if (username != null && (notificationType.startsWith("REFUND") || "ORDER_DELIVERED".equals(notificationType))) {
            smsNotificationChannel.trySend(username, title + "：" + content);
        }
        NotificationEntity notification = new NotificationEntity();
        notification.setUserId(userId);
        notification.setNotificationType(notificationType);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notification.setRelatedType(relatedType);
        notification.setIsRead(false);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> listMine(Long userId, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        var result = notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId,
                PageRequest.of(safePage, safeSize));
        List<Map<String, Object>> content = result.getContent().stream().map(NotificationService::toRow).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("totalElements", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("unreadCount", notificationRepository.countByUserIdAndIsReadFalse(userId));
        return payload;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> unreadCount(Long userId) {
        return Map.of("unreadCount", notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> markRead(Long userId, Long notificationId) {
        if (notificationRepository.findByIdAndUserId(notificationId, userId).isEmpty()) {
            throw new BizException("通知不存在", ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        int updated = notificationRepository.markRead(notificationId, userId);
        return Map.of("notificationId", notificationId, "read", true, "changed", updated > 0);
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> markAllRead(Long userId) {
        int updated = notificationRepository.markAllRead(userId);
        return Map.of("markedRead", updated);
    }

    private static Map<String, Object> toRow(NotificationEntity notification) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", notification.getId());
        row.put("title", notification.getTitle());
        row.put("content", notification.getContent());
        row.put("notificationType", notification.getNotificationType());
        row.put("relatedId", notification.getRelatedId());
        row.put("relatedType", notification.getRelatedType());
        row.put("isRead", Boolean.TRUE.equals(notification.getIsRead()));
        row.put("createdAt", notification.getCreatedAt());
        return row;
    }
}
