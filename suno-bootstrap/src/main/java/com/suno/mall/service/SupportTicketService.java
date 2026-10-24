package com.suno.mall.service;

import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.entity.SupportTicketEntity;
import com.suno.mall.dao.ResaleOrderRepository;
import com.suno.mall.dao.SupportTicketRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P2 客服工单：买家提交（可关联本人订单），运营回复并推进 OPEN → PROCESSING/CLOSED，
 * 回复推送站内通知。
 */
@Service
public class SupportTicketService {

    private static final Set<String> TICKET_TYPES = Set.of("QUESTION", "COMPLAINT", "AFTER_SALE", "OTHER");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "OPERATOR", "FINANCE");

    private final SupportTicketRepository supportTicketRepository;
    private final ResaleOrderRepository resaleOrderRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public SupportTicketService(SupportTicketRepository supportTicketRepository,
                                ResaleOrderRepository resaleOrderRepository,
                                NotificationService notificationService,
                                AuditLogService auditLogService) {
        this.supportTicketRepository = supportTicketRepository;
        this.resaleOrderRepository = resaleOrderRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Map<String, Object> create(Long userId, String ticketType, String content, String orderNo) {
        String type = ticketType == null ? "OTHER" : ticketType.trim().toUpperCase();
        if (!TICKET_TYPES.contains(type)) {
            throw new BizException("工单类型不合法", ErrorCode.PARAM_INVALID);
        }
        if (content == null || content.trim().length() < 5) {
            throw new BizException("请描述你遇到的问题（至少 5 个字）", ErrorCode.PARAM_INVALID);
        }
        if (orderNo != null && !orderNo.isBlank()) {
            var order = resaleOrderRepository.findWithDetailsByOrderNo(orderNo.trim())
                    .orElseThrow(() -> new BizException("关联订单不存在", ErrorCode.ORDER_NOT_FOUND));
            if (!order.getBuyerUser().getId().equals(userId)) {
                throw new BizException("仅可关联本人订单", ErrorCode.ORDER_NOT_OWNER);
            }
        }
        SupportTicketEntity ticket = new SupportTicketEntity();
        ticket.setTicketNo("TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket.setUserId(userId);
        ticket.setOrderNo(orderNo == null || orderNo.isBlank() ? null : orderNo.trim());
        ticket.setTicketType(type);
        ticket.setContent(content.trim());
        ticket.setStatus("OPEN");
        ticket.setCreatedAt(LocalDateTime.now());
        supportTicketRepository.save(ticket);
        return Map.of("ticketNo", ticket.getTicketNo(), "status", ticket.getStatus());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> listMine(Long userId, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        var result = supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage, safeSize));
        Map<String, Object> payload = pageOf(result.getContent().stream().map(SupportTicketService::toRow).toList(),
                result.getTotalElements(), safePage, safeSize);
        return payload;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 30)
    public Map<String, Object> adminPage(String status, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        List<SupportTicketEntity> all = supportTicketRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(ticket -> status == null || status.isBlank() || status.equals(ticket.getStatus()))
                .toList();
        List<Map<String, Object>> content = all.stream().skip((long) safePage * safeSize).limit(safeSize)
                .map(SupportTicketService::toRow).toList();
        return pageOf(content, all.size(), safePage, safeSize);
    }

    @Transactional
    public Map<String, Object> adminReply(Long ticketId, String reply, Boolean closeTicket, String operator) {
        SupportTicketEntity ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BizException("工单不存在", ErrorCode.PARAM_INVALID));
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new BizException("工单已关闭", ErrorCode.PARAM_INVALID);
        }
        if (reply == null || reply.trim().length() < 2) {
            throw new BizException("回复内容过短", ErrorCode.PARAM_INVALID);
        }
        ticket.setReply(reply.trim());
        ticket.setRepliedBy(operator == null ? "admin" : operator);
        ticket.setRepliedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticket.setStatus(Boolean.TRUE.equals(closeTicket) ? "CLOSED" : "PROCESSING");
        supportTicketRepository.save(ticket);
        notificationService.push(ticket.getUserId(), "TICKET_REPLIED", "客服已回复",
                "工单 " + ticket.getTicketNo() + " 有新的回复：" + reply.trim(), ticket.getId(), "SUPPORT_TICKET");
        auditLogService.logAction("SUPPORT_TICKET_REPLY", "SUPPORT_TICKET", ticket.getTicketNo(),
                "status=" + ticket.getStatus());
        return toRow(ticket);
    }

    static boolean isAdminRole(String role) {
        return role != null && ADMIN_ROLES.contains(role.trim().toUpperCase());
    }

    private static Map<String, Object> pageOf(List<Map<String, Object>> content, long total, int page, int size) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("totalElements", total);
        payload.put("totalPages", (total + size - 1) / size);
        payload.put("page", page);
        payload.put("size", size);
        return payload;
    }

    private static Map<String, Object> toRow(SupportTicketEntity ticket) {
        Map<String, Object> row = new HashMap<>();
        row.put("ticketId", ticket.getId());
        row.put("ticketNo", ticket.getTicketNo());
        row.put("userId", ticket.getUserId());
        row.put("orderNo", ticket.getOrderNo());
        row.put("ticketType", ticket.getTicketType());
        row.put("content", ticket.getContent());
        row.put("status", ticket.getStatus());
        row.put("reply", ticket.getReply());
        row.put("repliedAt", ticket.getRepliedAt());
        row.put("createdAt", ticket.getCreatedAt());
        return row;
    }
}
