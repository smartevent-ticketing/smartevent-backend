package com.smartevent.modules.identity.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.api.PageResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.identity.dto.request.GrantUserRoleRequest;
import com.smartevent.modules.identity.dto.response.AdminUserResponse;
import com.smartevent.modules.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin User Management", description = "Admin tra cứu tài khoản và cấp vai trò")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Danh sách người dùng, hỗ trợ tìm theo email hoặc họ tên")
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminUserService.listUsers(search, page, size));
    }

    @PostMapping("/{userId}/roles")
    @Operation(summary = "Cấp thêm role cho tài khoản; không thay thế role hiện có")
    public ApiResponse<AdminUserResponse> grantRole(
            @PathVariable UUID userId,
            @Valid @RequestBody GrantUserRoleRequest request,
            @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(adminUserService.grantRole(userId, request.roleName(), currentUser.getId()));
    }
}
