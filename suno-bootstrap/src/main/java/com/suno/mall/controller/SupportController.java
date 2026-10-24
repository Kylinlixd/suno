package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AuthApplicationService;
import com.suno.mall.service.SupportTicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * P2 客服工单（OPS-070/071 买家侧；OPS-072/073 管理端）。
 */
@Validated
@RestController
public class SupportController {

    private final SupportTicketService supportTicketService;
    private final AuthApplicationService authApplicationService;

    public SupportController(SupportTicketService supportTicketService,
                             AuthApplicationService authApplicationService) {
        this.supportTicketService = supportTicketService;
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/api/support/tickets")
    public ApiResponse<Map<String, Object>> createTicket(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CreateTicketRequest request
    ) {
        return ApiResponse.ok(supportTicketService.create(
                authApplicationService.requireUserId(jwt), request.ticketType(), request.content(), request.orderNo()));
    }

    @GetMapping("/api/support/tickets/my")
    public ApiResponse<Map<String, Object>> myTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(supportTicketService.listMine(
                authApplicationService.requireUserId(jwt), page, size));
    }

    @GetMapping("/api/admin/support/tickets")
    public ApiResponse<Map<String, Object>> adminTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        authApplicationService.requireUser(jwt);
        return ApiResponse.ok(supportTicketService.adminPage(status, page, size));
    }

    @PostMapping("/api/admin/support/tickets/{ticketId}/reply")
    public ApiResponse<Map<String, Object>> adminReply(
            @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.PathVariable Long ticketId,
            @RequestBody @Valid ReplyTicketRequest request
    ) {
        var operator = authApplicationService.requireUser(jwt);
        return ApiResponse.ok(supportTicketService.adminReply(ticketId, request.reply(), request.closeTicket(),
                operator.getUsername()));
    }

    public record CreateTicketRequest(String ticketType, @NotBlank String content, String orderNo) {
    }

    public record ReplyTicketRequest(@NotBlank String reply, Boolean closeTicket) {
    }
}
