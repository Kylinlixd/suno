package com.suno.mall.controller;

import com.suno.mall.entity.EventOutboxEntity;
import com.suno.mall.dao.EventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OPS-S002 事件派发调度器：消费 outbox PENDING 条目（至少一次投递）。
 * 当前投递目标为结构化日志；接 MQ/Webhook 时替换 deliver 一处。
 */
@Component
public class EventOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventOutboxDispatcher.class);

    private final EventOutboxRepository eventOutboxRepository;

    public EventOutboxDispatcher(EventOutboxRepository eventOutboxRepository) {
        this.eventOutboxRepository = eventOutboxRepository;
    }

    @Scheduled(fixedDelayString = "${operations.event-outbox.dispatch-fixed-delay-ms:5000}")
    @Transactional
    public int dispatchPending() {
        List<EventOutboxEntity> pending = eventOutboxRepository.findByStatusOrderByIdAsc("PENDING", PageRequest.of(0, 100));
        int dispatched = 0;
        for (EventOutboxEntity entry : pending) {
            log.info("outbox event {}#{} v{} payload={}", entry.getUseCaseId(), entry.getEventType(),
                    entry.getVersion(), entry.getPayload());
            dispatched += eventOutboxRepository.markPublished(entry.getId(), LocalDateTime.now());
        }
        return dispatched;
    }
}
