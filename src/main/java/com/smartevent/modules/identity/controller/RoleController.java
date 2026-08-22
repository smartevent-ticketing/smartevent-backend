package com.smartevent.modules.identity.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.modules.identity.dto.response.RoleResponse;
import com.smartevent.modules.identity.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Role & Permission Management", description = "APIs quản lý quyền hạn và vai trò người dùng")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các vai trò (Roles) trong hệ thống")
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return ApiResponse.success(roleService.getAllRoles());
    }
}

