package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M3 运营后台：用户管理（IDN-110~111）。
 */
@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> pageUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(adminUserService.pageUsers(keyword, status, page, size));
    }

    @PostMapping("/{userId}/status")
    public ApiResponse<Map<String, Object>> setUserStatus(
            @PathVariable Long userId,
            @RequestBody @Valid SetStatusRequest request
    ) {
        return ApiResponse.ok(adminUserService.setUserStatus(userId, request.accountStatus()));
    }

    public record SetStatusRequest(@NotBlank String accountStatus) {
    }
}
