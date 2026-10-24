package com.suno.mall.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suno.mall.core.event.DomainEvent;
import com.suno.mall.core.event.EventOutbox;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;
import com.suno.mall.entity.EventOutboxEntity;
import com.suno.mall.dao.EventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * P3 事件外发箱实现：领域事实与业务同事务落库（outbox 模式），
 * 派发调度器异步消费 PENDING 条目（当前投递目标为结构化日志，
 * 接 MQ/Webhook 时替换 deliver 一处）。至少一次投递，消费方需幂等。
 */
@Service
public class JpaEventOutbox implements EventOutbox {

    private static final Logger log = LoggerFactory.getLogger(JpaEventOutbox.class);

    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public JpaEventOutbox(EventOutboxRepository eventOutboxRepository, ObjectMapper objectMapper) {
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        UseCaseId useCaseId = event.getClass().getAnnotation(UseCaseId.class);
        EventVersion eventVersion = event.getClass().getAnnotation(EventVersion.class);
        if (useCaseId == null || eventVersion == null) {
            throw new IllegalArgumentException("未登记的领域事件: " + event.getClass().getName());
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            EventOutboxEntity entry = new EventOutboxEntity();
            entry.setUseCaseId(useCaseId.value());
            entry.setEventType(event.getClass().getSimpleName());
            entry.setVersion(eventVersion.value());
            entry.setPayload(payload);
            entry.setStatus("PENDING");
            entry.setCreatedAt(LocalDateTime.now());
            eventOutboxRepository.save(entry);
        } catch (Exception failure) {
            throw new IllegalStateException("事件序列化失败: " + failure.getMessage(), failure);
        }
    }

}
